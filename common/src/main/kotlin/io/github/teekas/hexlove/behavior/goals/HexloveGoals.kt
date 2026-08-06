package io.github.teekas.hexlove.behavior.goals

import io.github.teekas.hexlove.bond.BondGate
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.mixin.MixinSlimeMoveControl
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.player.Player
import java.util.EnumSet

/**
 * Marker for every goal this mod installs. Lets us guarantee cleanup even if the bookkeeping map
 * is lost (Requirement 25.7): `goalSelector.removeAllGoals { it is HexloveGoal }`.
 */
interface HexloveGoal

/**
 * A slime and a magma cube have no usable pathfinder and a move control that ignores a wanted
 * position: everything they do is a hop in a stored direction. Steering one means writing that
 * direction, which is what [MixinSlimeMoveControl] exists for.
 */
internal fun Mob.hopsInsteadOfWalking(): Boolean = moveControl is MixinSlimeMoveControl

private fun Mob.steerHops(target: LivingEntity, speed: Double): Boolean {
    val control = moveControl as? MixinSlimeMoveControl ?: return false
    val dx = target.x - x
    val dz = target.z - z
    val yaw = (Mth.atan2(dz, dx) * (180.0 / Math.PI)).toFloat() - 90.0f
    control.hexloveSetDirection(yaw, true)
    control.hexloveSetWantedMovement(speed)
    return true
}

/** Walk, or run if you have fallen behind. Shared by everything in this file that chases somebody. */
private fun Mob.pursue(target: LivingEntity, speed: Double) {
    if (steerHops(target, speed)) return
    if (!navigation.moveTo(target, speed)) {
        moveControl.setWantedPosition(target.x, target.y, target.z, speed)
    }
}

/**
 * Requirements 8.3, 8.4, revised: a fight over the beloved outranks standing next to them. The old
 * behavior kept the charmed creature glued to its owner at priority zero, so a zombie only swung at
 * an attacker that happened to wander into arm's reach. Now devotion steps aside for as long as
 * there is somebody to hit, and only pulls the creature back when it has strayed off the leash.
 */
private fun Mob.busyFightingFor(owner: LivingEntity, leash: Double): Boolean {
    val victim = target ?: return false
    if (!victim.isAlive || victim === owner) return false
    return distanceToSqr(owner) <= leash * leash
}

/**
 * Requirement 8.2: follow the owner. Works for any [Mob], not just [net.minecraft.world.entity.TamableAnimal],
 * and falls back to the move control for mobs whose navigation is useless (slimes and friends).
 *
 * Requirement 8.2, revised: if the beloved outruns it, the creature runs too — a charmed cow that
 * cannot keep up with a sprinting player is a charm that does not work.
 */
class FollowLoverGoal(
    private val mob: Mob,
    private val ownerSupplier: () -> LivingEntity?,
    /** How far the creature may stray from the one it keeps to before a fight stops mattering. */
    private val leash: () -> Double,
    private val config: () -> HexloveServerConfig.Love,
) : Goal(), HexloveGoal {
    private var owner: LivingEntity? = null

    init {
        // a hopping creature must keep the MOVE slot free: its own jumping goal owns it, and taking
        // it away is what left charmed magma cubes standing still
        flags = if (mob.hopsInsteadOfWalking()) EnumSet.of(Flag.LOOK) else EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        // Requirement 8.11: no owner in reach means the behavior pauses, the bond stays
        val candidate = ownerSupplier() ?: return false
        val cfg = config()
        val leashed = leash()
        forgetStrayedFight(candidate, leashed)
        if (mob.busyFightingFor(candidate, leashed)) return false
        val follow = cfg.followDistance
        if (mob.distanceToSqr(candidate) < follow * follow) return false
        owner = candidate
        return true
    }

    override fun canContinueToUse(): Boolean {
        val current = owner ?: return false
        if (!current.isAlive) return false
        val cfg = config()
        val leashed = leash()
        forgetStrayedFight(current, leashed)
        if (mob.busyFightingFor(current, leashed)) return false
        val stopDistance = cfg.followDistance * 0.5
        return mob.distanceToSqr(current) > stopDistance * stopDistance
    }

    /**
     * Off the leash the fight is over: the grudge is dropped outright rather than merely outranked.
     * Without this the creature came home, remembered, and set off again.
     */
    private fun forgetStrayedFight(owner: LivingEntity, leash: Double) {
        val victim = mob.target ?: return
        if (victim === owner) return
        if (mob.distanceToSqr(owner) <= leash * leash) return
        mob.target = null
        mob.lastHurtByMob = null
    }

    override fun stop() {
        owner = null
        mob.navigation.stop()
    }

    override fun tick() {
        val current = owner ?: return
        val cfg = config()
        mob.lookControl.setLookAt(current, 30f, 30f)
        val sprinting = mob.distanceToSqr(current) > cfg.sprintDistance * cfg.sprintDistance
        mob.pursue(current, if (sprinting) cfg.sprintSpeed else cfg.followSpeed)
    }
}

/**
 * Requirements 8.3, 8.4: fight whoever the beloved fights, and whoever hurts them — the way a tamed
 * wolf does, and no further.
 *
 * Two rules make the difference between a guard dog and a bloodhound, and the first version had
 * neither. A grudge counts only if it is *recent*, and only if it was picked up *after* the charm
 * began: reading `lastHurtMob` blind meant a freshly charmed creature marched off to finish a fight
 * its beloved had walked away from long ago. And the fight has to be near home — a creature that has
 * strayed past the leash is not defending anybody.
 */
class AssistOwnerTargetGoal(
    private val mob: Mob,
    private val ownerSupplier: () -> LivingEntity?,
    private val config: () -> HexloveServerConfig.Love,
) : Goal(), HexloveGoal {
    /** Nothing that happened before the bond existed is this creature's business. */
    private val bondBegan = mob.level().gameTime
    private var candidate: LivingEntity? = null

    init {
        flags = EnumSet.of(Flag.TARGET)
    }

    override fun canUse(): Boolean {
        val owner = ownerSupplier() ?: return false
        val cfg = config()
        // out past the leash the creature is going home, not hunting
        if (mob.distanceToSqr(owner) > cfg.combatLeashDistance * cfg.combatLeashDistance) return false

        val found = freshGrudge(owner, cfg.grudgeMemoryTicks) ?: return false
        if (found === mob || found === owner) return false
        // never turn on the owner, and never on somebody who loves the same owner (Requirement 4.5)
        if (BondGate.forbidsTargeting(mob, found)) return false
        candidate = found
        return true
    }

    /**
     * Holds the target slot for as long as the fight lasts. Without this the goal handed the slot back
     * the moment the grudge went stale — a hundred ticks into a fight the mob had not finished — and
     * whatever vanilla goal came next was free to pick somebody else, or nobody.
     */
    override fun canContinueToUse(): Boolean {
        val owner = ownerSupplier() ?: return false
        val victim = mob.target ?: return false
        if (!victim.isAlive || victim === owner) return false
        val cfg = config()
        if (mob.distanceToSqr(owner) > cfg.combatLeashDistance * cfg.combatLeashDistance) return false
        return !BondGate.forbidsTargeting(mob, victim)
    }

    /** Whoever the beloved struck or was struck by, if it happened recently and after the charm. */
    private fun freshGrudge(owner: LivingEntity, memory: Int): LivingEntity? {
        val sinceBond = mob.level().gameTime - bondBegan
        fun recent(timestamp: Int): Boolean {
            val age = (owner.tickCount - timestamp).toLong()
            return age in 0..memory.toLong() && age <= sinceBond
        }

        owner.lastHurtMob
            ?.takeIf { it.isAlive && recent(owner.lastHurtMobTimestamp) }
            ?.let { return it }
        return owner.lastHurtByMob?.takeIf { it.isAlive && recent(owner.lastHurtByMobTimestamp) }
    }

    override fun start() {
        // the setTarget mixin still gets the final say (Requirement 8.6)
        mob.target = candidate
        candidate = null
    }
}

/** Requirement 16.3: attack whatever comes near the protected entity, except the protected entity itself. */
class JealousGuardGoal(
    private val mob: Mob,
    private val protectedSupplier: () -> LivingEntity?,
    private val radiusSupplier: () -> Double,
) : Goal(), HexloveGoal {
    private var candidate: LivingEntity? = null

    init {
        flags = EnumSet.of(Flag.TARGET)
    }

    override fun canUse(): Boolean {
        val guarded = protectedSupplier() ?: return false
        val radius = radiusSupplier()
        // a jealous creature guards from arm's length: chasing somebody out of the ring is not guarding,
        // and it was what let jealous mobs disappear over the horizon
        if (mob.distanceToSqr(guarded) > radius * radius) return false
        val found = mob.level()
            .getEntitiesOfClass(LivingEntity::class.java, guarded.boundingBox.inflate(radius)) {
                it !== guarded && it !== mob && it.isAlive && !BondGate.forbidsTargeting(mob, it)
            }
            .minByOrNull { it.distanceToSqr(guarded) }
            ?: return false
        candidate = found
        return true
    }

    override fun start() {
        mob.target = candidate
        candidate = null
    }
}

/**
 * Requirement 16.4: a cow has no ATTACK_DAMAGE attribute at all, and attributes cannot be added
 * after registration. So the goal deals the configured damage itself and leaves attributes alone.
 *
 * Requirement 16.4, revised: it charges at a run. A cow that ambles towards its victim at grazing
 * speed is not jealous, it is curious.
 *
 * Revised again for slimes. A slime looks like it can fight and cannot: vanilla only ever deals its
 * damage through `playerTouch`, so a slime has no way at all to hurt another mob. A charmed one chased
 * its target faithfully and bounced off it forever. Now it borrows this goal too.
 */
class PassiveStrikeGoal(
    private val mob: Mob,
    private val damageSupplier: () -> Double,
    private val speedSupplier: () -> Double,
    private val cooldownTicks: Int = 20,
) : Goal(), HexloveGoal {
    private var cooldown = 0

    init {
        // same reason as the follow goal: a hopping creature needs its own MOVE slot
        flags = if (mob.hopsInsteadOfWalking()) EnumSet.of(Flag.LOOK) else EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        val target = mob.target ?: return false
        return target.isAlive
    }

    override fun canContinueToUse(): Boolean = canUse()

    override fun stop() {
        mob.navigation.stop()
    }

    override fun tick() {
        val target = mob.target ?: return
        mob.lookControl.setLookAt(target, 30f, 30f)
        mob.pursue(target, speedSupplier())
        if (cooldown > 0) {
            cooldown--
            return
        }
        val reach = mob.bbWidth * 2f + target.bbWidth
        if (mob.distanceToSqr(target) <= reach * reach) {
            target.hurt(mob.damageSources().mobAttack(mob), damageSupplier().toFloat())
            cooldown = cooldownTicks
        }
    }
}

/** Requirements 20.2, 20.4: look at the caller and walk to them, without touching target selection. */
class SirenLureGoal(
    private val mob: Mob,
    private val callerSupplier: () -> LivingEntity?,
    private val speed: Double = 1.0,
) : Goal(), HexloveGoal {
    private var caller: LivingEntity? = null

    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        caller = callerSupplier() ?: return false
        return true
    }

    override fun canContinueToUse() = caller?.isAlive == true

    override fun stop() {
        caller = null
        mob.navigation.stop()
    }

    override fun tick() {
        val current = caller ?: return
        mob.lookControl.setLookAt(current, 30f, 30f)
        if (mob.distanceToSqr(current) > 4.0) {
            mob.pursue(current, speed)
        } else {
            mob.navigation.stop()
        }
    }
}

/** A phantom has no body to attack; mesmerised creatures simply gather at its image. */
class PhantomIdealGoal(
    private val mob: Mob,
    private val idealSupplier: () -> LivingEntity?,
    private val speedSupplier: () -> Double,
) : Goal(), HexloveGoal {
    private var ideal: LivingEntity? = null
    private var repathCooldown = 0

    init {
        flags = if (mob.hopsInsteadOfWalking()) EnumSet.of(Flag.LOOK) else EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        ideal = idealSupplier() ?: return false
        return true
    }

    override fun canContinueToUse(): Boolean = ideal?.isAlive == true

    override fun start() {
        repathCooldown = 0
    }

    override fun stop() {
        ideal = null
        repathCooldown = 0
        mob.navigation.stop()
    }

    override fun tick() {
        val current = ideal ?: return
        mob.lookControl.setLookAt(current, 30f, 30f)
        if (mob.distanceToSqr(current) <= STOP_DISTANCE_SQUARED) {
            mob.navigation.stop()
            repathCooldown = 0
            return
        }

        val speed = speedSupplier()
        if (mob.hopsInsteadOfWalking()) {
            mob.pursue(current, speed)
            return
        }

        // Replacing a path every tick repeatedly resets vanilla navigation and produces visible
        // acceleration hitches. Keep following the current path and refresh it only as needed.
        if (repathCooldown > 0) {
            repathCooldown--
            return
        }
        repathCooldown = REPATH_INTERVAL
        if (!mob.navigation.moveTo(current, speed)) {
            mob.moveControl.setWantedPosition(current.x, current.y, current.z, speed)
        }
    }

    private companion object {
        const val REPATH_INTERVAL = 10
        const val STOP_DISTANCE_SQUARED = 4.0
    }
}

/**
 * Stage 3 — Humiliation advance: the animal walks towards the serene player and looks at them.
 *
 * The goal stops if the player is dead, logged off, or no longer serene. The actual payoff
 * (humiliation trigger at ≤2.0 blocks) is handled by NatureTicker, which already has the level
 * context needed to iterate live players. This goal only moves the animal.
 */
class AnimalAdvanceGoal(
    private val mob: Mob,
    private val player: Player,
) : Goal(), HexloveGoal {

    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean = player.isAlive

    override fun canContinueToUse(): Boolean = player.isAlive

    override fun stop() {
        mob.navigation.stop()
    }

    override fun tick() {
        mob.lookControl.setLookAt(player, 30f, 30f)
        // Close to well inside the 2.0-block courting radius and stay there. NatureTicker
        // requires COURTING_WINDOW_TICKS of continuous contact before the payoff triggers,
        // so the animal must genuinely stand next to the player like a breeding partner
        // instead of just brushing past.
        val holdSq = 1.4 * 1.4
        if (mob.distanceToSqr(player) > holdSq) {
            mob.pursue(player, 1.0)
        } else {
            mob.navigation.stop()
        }
    }
}

/**
 * Requirement 11, revised: the whole point of the fix. The betrothed walks to its mate and stays
 * there. The children are the ticker's business — this goal only does the walking, so distance is
 * something the pair actually has to close.
 */
class CourtshipGoal(
    private val mob: Mob,
    private val mateSupplier: () -> LivingEntity?,
    private val config: () -> HexloveServerConfig.Breeding,
) : Goal(), HexloveGoal {
    private var mate: LivingEntity? = null

    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean {
        mate = mateSupplier() ?: return false
        return true
    }

    override fun canContinueToUse() = mate?.isAlive == true

    override fun stop() {
        mate = null
        mob.navigation.stop()
    }

    override fun tick() {
        val current = mate ?: return
        val cfg = config()
        mob.lookControl.setLookAt(current, 30f, 30f)
        // stop just short of overlapping, so the two stand together instead of shoving each other
        val hold = (cfg.courtshipContactDistance * 0.6).coerceAtLeast(0.8)
        if (mob.distanceToSqr(current) > hold * hold) {
            mob.pursue(current, cfg.courtshipSpeed)
        } else {
            mob.navigation.stop()
        }
    }
}
