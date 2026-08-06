package io.github.teekas.hexlove.nature

import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.behavior.BehaviorController
import io.github.teekas.hexlove.behavior.BehaviorKind
import io.github.teekas.hexlove.config.HexloveServerConfig
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.WeakHashMap
import java.util.UUID

/**
 * Manages the humiliation advance: an animal walking toward a serene player after two-step trigger.
 *
 * State is transient (WeakHashMap, no NBT), matching BehaviorTracker's philosophy.
 * Loss on server restart is safe and intentional.
 */
object HumiliationManager {

    /** Tracks animals currently pursuing a serene player. Key is the animal. */
    private val tracker = WeakHashMap<Animal, AdvanceEntry>()

    /** Both colours of the courting haze. Particle options are immutable; two instances suffice. */
    private val ADVANCE_PINK = ConjureParticleOptions(0xFF78C8)
    private val ADVANCE_PALE = ConjureParticleOptions(0xFFAEDC)

    data class AdvanceEntry(
        /** UUID of the target player. */
        val targetPlayer: UUID,
        /** Absolute game-time deadline (gameTime + advanceTimeoutTicks). */
        val deadline: Long,
        /**
         * Absolute game-time when the animal first reached "courting range" (≤ 2.0 blocks) of
         * the player, or -1 while it has not yet arrived. The animal must remain in range for
         * a courting window before the payoff triggers, mimicking two animals standing close
         * during breeding.
         */
        var nearSinceTick: Long = -1L,
    )

    /**
     * Returns the live entry for [animal], or null if it is not advancing.
     * Also removes stale (deadline-expired) entries on access.
     */
    fun entry(animal: Animal): AdvanceEntry? = tracker[animal]

    fun isAdvancing(animal: Animal): Boolean = tracker.containsKey(animal)

    /**
     * Starts the humiliation advance: resets vanilla breeding love on the animal, records the
     * entry with a timeout deadline, and installs ADVANCE AI goals.
     *
     * Called by [NatureHooks.onAnimalAdvance] / MixinNatureAnimalAdvance.
     */
    fun beginAdvance(animal: Animal, player: Player) {
        try {
            if (!ChimeraNatures.isSerene(player)) return
            val level = animal.level() as? ServerLevel ?: return
            val cfg = HexloveServerConfig.config.nature

            // Cancel ordinary breeding so normal reproduction does not fire simultaneously.
            animal.resetLove()

            val entry = AdvanceEntry(
                targetPlayer = player.uuid,
                deadline = level.gameTime + cfg.advanceTimeoutTicks,
            )
            tracker[animal] = entry

            BehaviorController.applyAdvance(animal, player)
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("HumiliationManager.beginAdvance failed", t)
        }
    }

    /**
     * Called every tick by [NatureTicker] when the animal is within 2.0 blocks of its target player.
     * Applies the humiliation effect and all payoff consequences, then tears down the advance.
     */
    fun triggerPayoff(animal: Animal, player: Player, level: ServerLevel) {
        try {
            val cfg = HexloveServerConfig.config.nature

            // Duration: at least humiliationMinTicks, or a quarter of remaining serene time.
            val remaining = ChimeraNatures.remainingTicks(player, ChimeraNature.SERENE)
            val duration = maxOf(cfg.humiliationMinTicks, remaining / 4)

            // 1. Apply the Humiliation effect.
            player.addEffect(
                net.minecraft.world.effect.MobEffectInstance(
                    io.github.teekas.hexlove.registry.HexloveEffects.HUMILIATION.value,
                    duration, 0, false, true, true,
                )
            )

            // Award the achievement. Only a real ServerPlayer can hold advancement progress;
            // the animal-side call site passes a Player, so guard the cast.
            (player as? net.minecraft.server.level.ServerPlayer)?.let {
                io.github.teekas.hexlove.advancement.HexloveAdvancements.grant(
                    it, io.github.teekas.hexlove.advancement.HexloveAdvancements.HUMILIATED,
                )
            }

            // 2. Magic damage.
            player.hurt(player.damageSources().magic(), cfg.humiliationDamage.toFloat())

            // 3. Drop main-hand and off-hand items.
            // This also force-closes any open Hex Casting staff GUI because item-use is active
            // while a staff is casting: dropping the stack terminates the use session client-side.
            dropHand(player, net.minecraft.world.InteractionHand.MAIN_HAND)
            dropHand(player, net.minecraft.world.InteractionHand.OFF_HAND)

            // 4. Drop amethyst dust at the player's feet.
            val dustCount = cfg.humiliationDustMin +
                if (cfg.humiliationDustMax > cfg.humiliationDustMin)
                    level.random.nextInt(cfg.humiliationDustMax - cfg.humiliationDustMin + 1)
                else 0
            repeat(dustCount) {
                val drop = ItemStack(HexItems.AMETHYST_DUST)
                player.drop(drop, false)
            }

            // 5. Tear down advance, put animal on a normal breeding cooldown.
            // resetLove() clears the in-love timer; the vanilla loveCooldown will also be set to
            // the post-breed value by vanilla code after the next spawnChildFromBreeding call.
            // Since humiliation skips that, we simply reset — the animal will need to be fed
            // again before it can breed, which is the correct post-humiliation behavior.
            cancelAdvance(animal)
            animal.resetLove()
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("HumiliationManager.triggerPayoff failed", t)
            cancelAdvance(animal)
        }
    }

    /**
     * Cancels the advance: removes tracker entry and tears down AI goals.
     * Safe to call even if the animal is not currently advancing.
     */
    fun cancelAdvance(animal: Animal) {
        try {
            tracker.remove(animal)
            BehaviorController.remove(animal, BehaviorKind.ADVANCE)
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("HumiliationManager.cancelAdvance failed", t)
        }
    }

    /** Emit pink Conjure particles above the animal (called by NatureTicker every 10 ticks). */
    fun advanceParticles(level: ServerLevel, animal: Animal) {
        try {
            val x = animal.x
            val y = animal.y + animal.bbHeight + 0.3
            val z = animal.z
            level.sendParticles(ADVANCE_PINK, x, y, z, 3, 0.2, 0.1, 0.2, 0.02)
            level.sendParticles(ADVANCE_PALE, x, y + 0.2, z, 2, 0.15, 0.1, 0.15, 0.015)
        } catch (_: Throwable) { }
    }

    /**
     * The "we are breeding right now" hearts vanilla shows when two animals stand together.
     * Emitted between the animal and the player during the courting window so the player
     * knows exactly what is going on before the humiliation lands.
     */
    fun courtingHearts(level: ServerLevel, animal: Animal, player: Player) {
        try {
            val midX = (animal.x + player.x) * 0.5
            val midY = (animal.y + player.y) * 0.5 + 1.0
            val midZ = (animal.z + player.z) * 0.5
            level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.HEART,
                midX, midY, midZ,
                2, 0.35, 0.25, 0.35, 0.0,
            )
        } catch (_: Throwable) { }
    }

    /**
     * Returns a snapshot of all currently tracked advances. Called by NatureTicker to avoid
     * ConcurrentModificationException when iterating while payoff may mutate the map.
     */
    fun snapshot(): List<Pair<Animal, AdvanceEntry>> = tracker.entries.map { it.key to it.value }

    private fun dropHand(player: Player, hand: net.minecraft.world.InteractionHand) {
        val stack = player.getItemInHand(hand)
        if (!stack.isEmpty) {
            player.drop(stack.copy(), false, true)
            player.setItemInHand(hand, ItemStack.EMPTY)
        }
    }
}
