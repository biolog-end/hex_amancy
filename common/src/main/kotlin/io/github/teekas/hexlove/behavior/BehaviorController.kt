package io.github.teekas.hexlove.behavior

import io.github.teekas.hexlove.bond.BondGate
import io.github.teekas.hexlove.bond.CourtshipState
import io.github.teekas.hexlove.bond.JealousyBond
import io.github.teekas.hexlove.bond.LoveBond
import io.github.teekas.hexlove.bond.PhantomIdealState
import io.github.teekas.hexlove.bond.SirenState
import io.github.teekas.hexlove.behavior.goals.AnimalAdvanceGoal
import io.github.teekas.hexlove.behavior.goals.AssistOwnerTargetGoal
import io.github.teekas.hexlove.behavior.goals.CourtshipGoal
import io.github.teekas.hexlove.behavior.goals.FollowLoverGoal
import io.github.teekas.hexlove.behavior.goals.HexloveGoal
import io.github.teekas.hexlove.behavior.goals.JealousGuardGoal
import io.github.teekas.hexlove.behavior.goals.PassiveStrikeGoal
import io.github.teekas.hexlove.behavior.goals.PhantomIdealGoal
import io.github.teekas.hexlove.behavior.goals.SirenLureGoal
import io.github.teekas.hexlove.behavior.goals.hopsInsteadOfWalking
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.sameLevelLiving
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal
import net.minecraft.world.entity.ai.goal.FollowParentGoal
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.PanicGoal
import net.minecraft.world.entity.ai.goal.RandomStrollGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.TemptGoal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import java.util.WeakHashMap

enum class BehaviorKind { LOVE, JEALOUSY, SIREN, PHEROMONE, PHANTOM_IDEAL, COURTSHIP, ADVANCE }

private enum class SelectorKind { GOAL, TARGET }

private data class InstalledGoal(val kind: BehaviorKind, val selector: SelectorKind, val goal: Goal)

private data class ParkedGoal(val kind: BehaviorKind, val priority: Int, val goal: Goal)

/**
 * Goals are never serialized by Minecraft, so behavior is not saved — it is *reinstalled* from the
 * persisted bond data when the entity loads.
 *
 * Two kinds of surgery happen here:
 *  - our own goals are *added* (and later removed again);
 *  - a short list of vanilla goals that directly contradict being charmed is *parked* — taken out
 *    and put back untouched when the bond ends. Requirement 8.8: "restore the original behavior" is
 *    exactly subtracting our additions and un-parking what we parked.
 *
 * Parking is what stops a charmed cow from strolling off or bolting when it is hit: it endures.
 */
object BehaviorController {
    private val installed = WeakHashMap<Mob, MutableList<InstalledGoal>>()
    private val parked = WeakHashMap<Mob, MutableList<ParkedGoal>>()

    /**
     * Vanilla goals that fight against being charmed or courting: panic, fear, aimless wandering and
     * being tempted away by food. Everything else — floating, breathing, eating grass, fleeing lava —
     * is left exactly as it was.
     */
    private fun contradictsDevotion(goal: Goal): Boolean =
        goal is PanicGoal ||
            goal is AvoidEntityGoal<*> ||
            goal is RandomStrollGoal ||
            goal is RandomLookAroundGoal ||
            goal is TemptGoal ||
            goal is FollowParentGoal

    /** Requirement 8.5: feature-based, not a species list. */
    fun hasMeleeAttack(mob: Mob): Boolean =
        mob.getAttribute(Attributes.ATTACK_DAMAGE) != null || mob is Enemy

    /**
     * Creatures that need the spell to land the blow for them: the ones with no attack at all, and the
     * ones that only *look* armed. A slime counts as a monster and has no way to hurt anything but a
     * player, so it is in the second group.
     */
    private fun needsHelpStriking(mob: Mob): Boolean = !hasMeleeAttack(mob) || mob.hopsInsteadOfWalking()

    fun applyLove(mob: Mob, bond: LoveBond) {
        remove(mob, BehaviorKind.LOVE)
        val ownerSupplier = { mob.sameLevelLiving(bond.target) }

        park(mob, BehaviorKind.LOVE)
        // priority 0: devotion outranks everything the mob would rather be doing
        add(
            mob, BehaviorKind.LOVE, SelectorKind.GOAL, 0,
            FollowLoverGoal(
                mob,
                ownerSupplier,
                { HexloveServerConfig.config.love.combatLeashDistance },
            ) { HexloveServerConfig.config.love },
        )
        // Requirement 8.5: love does not teach anybody to fight. A charmed cow follows and endures;
        // only jealousy hands a weapon to something that never had one.
        if (hasMeleeAttack(mob)) {
            // priority 0, above every vanilla target goal: defending the beloved is not one option among
            // several. At priority 1 a mob already retaliating against something else refused to switch,
            // which is exactly the "sometimes it just watches" complaint.
            add(
                mob, BehaviorKind.LOVE, SelectorKind.TARGET, 0,
                AssistOwnerTargetGoal(mob, ownerSupplier) { HexloveServerConfig.config.love },
            )
            // a slime picks its fights like a monster and then cannot finish them: vanilla only ever
            // deals slime damage to players, so this is the one case where the spell lands the blow
            if (mob.hopsInsteadOfWalking()) add(mob, BehaviorKind.LOVE, SelectorKind.GOAL, 1, strike(mob))
        }
    }

    private fun strike(mob: Mob) = PassiveStrikeGoal(
        mob,
        { HexloveServerConfig.config.jealousy.passiveDamage },
        { HexloveServerConfig.config.jealousy.chaseSpeed },
    )

    fun applyJealousy(mob: Mob, bond: JealousyBond) {
        remove(mob, BehaviorKind.JEALOUSY)
        val protectedSupplier = { mob.sameLevelLiving(bond.target) }

        park(mob, BehaviorKind.JEALOUSY)
        // Requirement 16, revised: jealousy is not a watchtower. It follows the one it is jealous over,
        // the same way love does, and steps aside the moment there is somebody to drive off.
        add(
            mob, BehaviorKind.JEALOUSY, SelectorKind.GOAL, 0,
            // the guard radius is also the leash: a jealous creature never leaves the ring it guards
            FollowLoverGoal(
                mob,
                protectedSupplier,
                { HexloveServerConfig.config.jealousy.guardRadius },
            ) { HexloveServerConfig.config.love },
        )
        add(
            mob, BehaviorKind.JEALOUSY, SelectorKind.TARGET, 1,
            JealousGuardGoal(mob, protectedSupplier) { HexloveServerConfig.config.jealousy.guardRadius },
        )
        if (needsHelpStriking(mob)) {
            add(mob, BehaviorKind.JEALOUSY, SelectorKind.GOAL, 1, strike(mob))
        }
    }

    fun applySiren(mob: Mob, state: SirenState) {
        remove(mob, BehaviorKind.SIREN)
        add(
            mob, BehaviorKind.SIREN, SelectorKind.GOAL, 1,
            SirenLureGoal(mob, { mob.sameLevelLiving(state.caller) }),
        )
    }

    /** Pheromones do not replace a creature's personality; they simply make every target invalid. */
    fun applyPheromone(mob: Mob) {
        remove(mob, BehaviorKind.PHEROMONE)
        mob.target = null
        mob.lastHurtByMob = null
    }

    fun applyPhantomIdeal(mob: Mob, state: PhantomIdealState) {
        remove(mob, BehaviorKind.PHANTOM_IDEAL)
        mob.target = null
        mob.lastHurtByMob = null
        add(
            mob, BehaviorKind.PHANTOM_IDEAL, SelectorKind.GOAL, 0,
            PhantomIdealGoal(mob, { mob.sameLevelLiving(state.ideal) }) {
                HexloveServerConfig.config.phantomIdeal.lureSpeed
            },
        )
    }

    fun applyCourtship(mob: Mob, state: CourtshipState) {
        remove(mob, BehaviorKind.COURTSHIP)
        park(mob, BehaviorKind.COURTSHIP)
        add(
            mob, BehaviorKind.COURTSHIP, SelectorKind.GOAL, 0,
            CourtshipGoal(
                mob,
                { mob.sameLevelLiving(state.mate) },
                { HexloveServerConfig.config.breeding },
            ),
        )
    }

    /**
     * Parks the same conflicting goals as courtship/love, then installs [AnimalAdvanceGoal] so
     * the animal walks towards the serene player and looks at them.
     *
     * BehaviorKind.ADVANCE is fully torn down by the existing [remove] path: nothing special is
     * needed because ADVANCE only ever adds GOAL-selector entries, never TARGET entries.
     *
     * Stage 4: nothing here is ferocious-specific — ADVANCE is purely serene-side.
     */
    fun applyAdvance(mob: Mob, player: Player) {
        remove(mob, BehaviorKind.ADVANCE)
        park(mob, BehaviorKind.ADVANCE)
        add(
            mob, BehaviorKind.ADVANCE, SelectorKind.GOAL, 0,
            AnimalAdvanceGoal(mob, player),
        )
    }

    /**
     * Requirement 8.6, revised: vetoing `setTarget` is not enough for a mob that already had one.
     * A skeleton keeps shooting at whatever `getTarget()` still returns, so the target has to be
     * dropped outright when it becomes forbidden.
     */
    fun forgetForbiddenTarget(mob: Mob) {
        val current = mob.target ?: return
        if (BondGate.forbidsTargeting(mob, current)) {
            mob.target = null
            mob.lastHurtByMob = null
        }
    }

    /** Removes exactly the goal instances we added for this kind. */
    fun remove(mob: Mob, kind: BehaviorKind) {
        val list = installed[mob]
        if (list != null) {
            val it = list.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (entry.kind != kind) continue
                selector(mob, entry.selector).removeGoal(entry.goal)
                it.remove()
            }
            if (list.isEmpty()) installed.remove(mob)
        }
        unpark(mob, kind)
    }

    fun removeAll(mob: Mob) {
        installed.remove(mob)?.forEach { selector(mob, it.selector).removeGoal(it.goal) }
        for (kind in BehaviorKind.entries) unpark(mob, kind)
        // safety net in case the bookkeeping map was lost (server restart, exception, other mod)
        mob.goalSelector.removeAllGoals { it is HexloveGoal }
        mob.targetSelector.removeAllGoals { it is HexloveGoal }
    }

    fun hasInstalled(mob: Mob, kind: BehaviorKind): Boolean =
        installed[mob]?.any { it.kind == kind } == true

    private fun park(mob: Mob, kind: BehaviorKind) {
        val list = parked.getOrPut(mob) { mutableListOf() }
        if (list.any { it.kind == kind }) return
        val already = list.map { it.goal }.toSet()
        val toPark = mob.goalSelector.availableGoals
            .filter { contradictsDevotion(it.goal) && it.goal !in already }
            .map { ParkedGoal(kind, it.priority, it.goal) }
        for (entry in toPark) {
            mob.goalSelector.removeGoal(entry.goal)
            list.add(entry)
        }
        if (list.isEmpty()) parked.remove(mob)
    }

    private fun unpark(mob: Mob, kind: BehaviorKind) {
        val list = parked[mob] ?: return
        val it = list.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.kind != kind) continue
            mob.goalSelector.addGoal(entry.priority, entry.goal)
            it.remove()
        }
        if (list.isEmpty()) parked.remove(mob)
    }

    private fun add(mob: Mob, kind: BehaviorKind, selector: SelectorKind, priority: Int, goal: Goal) {
        selector(mob, selector).addGoal(priority, goal)
        installed.getOrPut(mob) { mutableListOf() }.add(InstalledGoal(kind, selector, goal))
    }

    private fun selector(mob: Mob, kind: SelectorKind) =
        if (kind == SelectorKind.GOAL) mob.goalSelector else mob.targetSelector
}
