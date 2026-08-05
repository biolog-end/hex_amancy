package io.github.teekas.hexlove.nature

import io.github.teekas.hexlove.Hexlove
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.AbstractSkeleton
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.Spider
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.entity.animal.IronGolem
import net.minecraft.world.entity.animal.SnowGolem
import net.minecraft.world.entity.player.Player
import java.util.UUID
import java.util.WeakHashMap

/**
 * Gate for nature-based targeting rules, analogous to [io.github.teekas.hexlove.bond.BondGate].
 *
 * Never throws — runs inside vanilla target selection.
 *
 * Stage 3: serene players cannot be targeted by Animals.
 * Stage 4: ferocious players are neutral to specific undead/hostile mobs unless a grudge exists.
 *          Golems are explicitly NOT vetoed here — they must always be able to target ferocious players.
 */
object NatureGate {

    /**
     * Transient map: mob → set of ferocious player UUIDs that mob holds a grudge against.
     *
     * Populated from [recordGrudge] when a ferocious player damages a mob that would otherwise
     * be vetoed. Keyed by WeakReference (via WeakHashMap) so mob unload automatically clears it.
     * This is intentionally not persisted across restarts, matching the philosophy of BehaviorTracker.
     */
    private val grudges: WeakHashMap<LivingEntity, MutableSet<UUID>> = WeakHashMap()

    /**
     * Records a grudge: [mob] will now target [playerUuid] even if the ferocious neutrality
     * rule would otherwise veto it. The grudge lasts until the mob unloads.
     */
    fun recordGrudge(mob: LivingEntity, playerUuid: UUID) {
        grudges.getOrPut(mob) { mutableSetOf() }.add(playerUuid)
    }

    /**
     * Returns `true` when the [attacker] must not target [candidate] due to nature rules.
     *
     * This is wired as the first clause in [io.github.teekas.hexlove.bond.BondGate.forbidsTargeting],
     * so no additional mixin on `Mob.setTarget` is needed.
     *
     * Note: IronGolem and SnowGolem are explicitly NOT vetoed here — the NatureTicker assigns them
     * as attackers of ferocious players, and that targeting must not be blocked.
     */
    @JvmStatic
    fun forbidsTargeting(attacker: Entity, candidate: Entity): Boolean {
        return try {
            // Stage 3: an Animal must not target a serene player.
            if (attacker is Animal && candidate is Player && ChimeraNatures.isSerene(candidate)) return true

            // Stage 4: ferocious-player / hostile neutrality ("one of the pack").
            // Zombies, AbstractSkeletons, Creepers, Spiders, Phantoms (and their subtypes)
            // must not target a ferocious player — unless they hold a personal grudge.
            // Golems are excluded from this check (they are targeted by NatureTicker, not vetoed).
            // EnderMan is deliberately excluded from the protection list — they operate differently.
            if (candidate is Player && ChimeraNatures.isFerocious(candidate)) {
                if (attacker is IronGolem || attacker is SnowGolem) {
                    // Golems must always be able to target ferocious players — never veto them.
                    return false
                }
                if (isFerociousPackMob(attacker)) {
                    // Veto only if the mob has no grudge against this player.
                    val mobGrudges = grudges[attacker]
                    if (mobGrudges == null || !mobGrudges.contains(candidate.uuid)) {
                        return true
                    }
                }
            }

            false
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("NatureGate.forbidsTargeting failed", t)
            false
        }
    }

    /**
     * Returns true for mob types that are neutral to a ferocious player by default.
     * Subtypes are included via `is` checks on the base classes.
     * EnderMan is intentionally excluded.
     */
    private fun isFerociousPackMob(entity: Entity): Boolean =
        entity is Zombie ||
            entity is AbstractSkeleton ||
            entity is Creeper ||
            entity is Spider ||
            entity is Phantom

    /**
     * Public helper used by the LIVING_HURT handler to decide whether to record a grudge.
     * Returns true if [entity] is a mob type that the ferocious neutrality rule protects against.
     * Same logic as [isFerociousPackMob] but exposed for the event handler in HexloveEvents.
     */
    fun isFerociousPackTarget(entity: LivingEntity): Boolean = isFerociousPackMob(entity)
}
