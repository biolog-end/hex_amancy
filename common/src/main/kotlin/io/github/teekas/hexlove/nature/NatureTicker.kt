package io.github.teekas.hexlove.nature

import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.behavior.BehaviorController
import io.github.teekas.hexlove.config.HexloveServerConfig
import net.minecraft.core.particles.ItemParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.IronGolem
import net.minecraft.world.entity.animal.SnowGolem
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Server-side per-level ticker for chimera-nature mechanics. Registered in HexloveEvents.init()
 * next to the existing BondTicker.tick(level) registration.
 *
 * Tick cadence mirrors BondTicker:
 *   - every 3    : humiliation drip particles (the 3-tick interval spec'd)
 *   - every 10   : advance progression/timeout, forgetForbiddenTarget for serene/ferocious players,
 *                  ferocious idle exhaustion, iron/snow golem target assignment
 */
object NatureTicker {

    /** How long the animal must remain close to the player before humiliation triggers. */
    private const val COURTING_WINDOW_TICKS = 40L

    /** Cached snowball crush particle option — its shards are tiny white item fragments with real
     *  gravity, so a burst above the head reads as fat rain running off the face. */
    private val SNOWBALL_RAIN by lazy {
        ItemParticleOption(ParticleTypes.ITEM, ItemStack(Items.SNOWBALL))
    }

    /** Hex conjure particle colours for the nature trail. */
    private const val SERENE_HEX_RGB = 0x6ADB2A     // vivid leaf green
    private const val FEROCIOUS_HEX_RGB = 0xC81020  // hot blood red

    fun tick(level: ServerLevel) {
        try {
            val now = level.gameTime

            // Humiliation drip particles — every 3 ticks.
            if (now % 3L == 0L) {
                for (player in level.players()) {
                    if (ChimeraNatures.isHumiliated(player)) {
                        emitHumiliationDrips(level, player)
                    }
                }
            }

            // Nature aura trails — every 4 ticks. So bystanders can tell at a glance which
            // way the player just turned: green sparks for serene, red for ferocious.
            if (now % 4L == 0L) {
                for (player in level.players()) {
                    when (ChimeraNatures.of(player)) {
                        ChimeraNature.SERENE    -> emitHeadTrail(level, player, SERENE_HEX_RGB)
                        ChimeraNature.FEROCIOUS -> emitHeadTrail(level, player, FEROCIOUS_HEX_RGB)
                        null                    -> { /* no trail */ }
                    }
                }
            }

            // Every 10 ticks: advance progression, timeouts, forget-target passes, and ferocious effects.
            if (now % 10L == 0L) {
                tickAdvances(level, now)
                forgetForSerene(level)
                tickFerocious(level)
            }
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("NatureTicker.tick failed in {}", level.dimension().location(), t)
        }
    }

    /**
     * White droplets raining off the whole periphery of the head. Vanilla has no white raindrop
     * particle — every "raindrop" candidate (falling_water, dripping_water) is dyed cyan. So we
     * use the shard particle spawned when a Snowball projectile crushes (ItemParticleOption on
     * SNOWBALL): a small light-blue-white item fragment that inherits proper item-drop gravity
     * and physics. A ring of them above the head reads as streaks of white rain shed off the
     * face rather than a fireworks sparkle.
     */
    private fun emitHumiliationDrips(level: ServerLevel, player: ServerPlayer) {
        try {
            val cy = player.y + player.eyeHeight.toDouble() + 0.10          // just above the eyes
            val radius = 0.34
            val n = 5                                                        // drops per tick-interval
            val rng = level.random
            for (i in 0 until n) {
                val a = rng.nextDouble() * (Math.PI * 2.0)
                val x = player.x + Math.cos(a) * radius
                val z = player.z + Math.sin(a) * radius
                // count=0 turns the last four args into a direct motion vector; a small downward
                // push gives the shards an initial nudge that combines with item gravity for a
                // rain-like fall instead of a floaty drift.
                level.sendParticles(
                    SNOWBALL_RAIN,
                    x, cy, z,
                    0, 0.0, -0.08, 0.0, 1.0,
                )
            }
        } catch (_: Throwable) { }
    }

    /**
     * Two Hex conjure particles a tick-cycle drifting upward from the head, tinted to match the
     * active nature. Serene sheds mossy-green motes, ferocious sheds blood-red ones — even from
     * across the room another player can tell which way you turned without seeing the HUD.
     */
    private fun emitHeadTrail(level: ServerLevel, player: ServerPlayer, rgb: Int) {
        try {
            val cy = player.y + player.eyeHeight.toDouble() + 0.20
            level.sendParticles(
                ConjureParticleOptions(rgb),
                player.x, cy, player.z,
                2, 0.14, 0.10, 0.14, 0.02,
            )
        } catch (_: Throwable) { }
    }

    /** Process all ongoing humiliation advances: emit particles, check payoff, handle timeouts. */
    private fun tickAdvances(level: ServerLevel, now: Long) {
        for ((animal, entry) in HumiliationManager.snapshot()) {
            try {
                if (!animal.isAlive || animal.level() !== level) continue

                // Particles every 10 ticks (we are already in the 10-tick branch).
                HumiliationManager.advanceParticles(level, animal)

                // Deadline check.
                if (now >= entry.deadline) {
                    HumiliationManager.cancelAdvance(animal)
                    continue
                }

                // Payoff: animal reached the player.
                val player = level.getPlayerByUUID(entry.targetPlayer) ?: continue
                if (!player.isAlive) {
                    HumiliationManager.cancelAdvance(animal)
                    continue
                }
                if (!ChimeraNatures.isSerene(player)) {
                    HumiliationManager.cancelAdvance(animal)
                    continue
                }

                // Courting: once the animal is in range, it must stand there for a while before
                // the payoff triggers — mimicking two animals standing close during breeding.
                // Love-hearts are emitted throughout that window so the player understands what
                // is happening on-screen.
                val inRange = animal.distanceToSqr(player) <= 2.0 * 2.0
                if (inRange) {
                    if (entry.nearSinceTick < 0L) entry.nearSinceTick = now
                    HumiliationManager.courtingHearts(level, animal, player)
                    if (now - entry.nearSinceTick >= COURTING_WINDOW_TICKS) {
                        HumiliationManager.triggerPayoff(animal, player, level)
                    }
                } else {
                    // Broke contact — reset the timer so half-hearted approaches do not payoff.
                    entry.nearSinceTick = -1L
                }
            } catch (t: Throwable) {
                Hexlove.LOGGER.error("NatureTicker advance tick failed for animal {}", animal.uuid, t)
                HumiliationManager.cancelAdvance(animal)
            }
        }
    }

    /**
     * Drops existing animal targets that point at a serene player, within 24 blocks of any
     * serene player. Mirrors BehaviorController.forgetForbiddenTarget used by BondTicker.
     *
     * Stage 4: also clears hostile-mob targets that point at a ferocious player (unless grudge),
     * within 24 blocks of any ferocious player. Extended from the serene loop rather than written
     * as a separate scan, matching the spec ("extend it rather than writing a second scan").
     */
    private fun forgetForSerene(level: ServerLevel) {
        for (player in level.players()) {
            val box = player.boundingBox.inflate(24.0)
            if (ChimeraNatures.isSerene(player)) {
                val animals = level.getEntitiesOfClass(Animal::class.java, box) { it.isAlive }
                for (animal in animals) {
                    BehaviorController.forgetForbiddenTarget(animal)
                }
            }
            if (ChimeraNatures.isFerocious(player)) {
                // Clear any hostile mobs within 24 blocks that are targeting this ferocious player
                // but no longer should be (i.e., they have no grudge). forgetForbiddenTarget already
                // delegates to BondGate.forbidsTargeting, which calls NatureGate.forbidsTargeting.
                val hostiles = level.getEntitiesOfClass(Mob::class.java, box) { it.isAlive }
                for (hostile in hostiles) {
                    BehaviorController.forgetForbiddenTarget(hostile)
                }
            }
        }
    }

    /**
     * Stage 4: per-10-tick ferocious nature effects.
     *
     * 1. Passive idle exhaustion: every tick a ferocious player should receive
     *    [ferociousIdleExhaustion] exhaustion. We run every 10 ticks, so multiply by 10.
     *    causeFoodExhaustion is also multiplied by MixinNatureExhaustion (ferociousExhaustionMultiplier × 2.0),
     *    so the combined effect gives "fast metabolism while standing still" — the doubling is intentional
     *    per spec: the idle drain stacks with the exhaust multiplier.
     *
     * 2. Iron/Snow golem aggro: every IronGolem and SnowGolem within 16 blocks of a ferocious
     *    player receives setTarget(player). NatureGate does NOT veto golems, so the target sticks.
     */
    private fun tickFerocious(level: ServerLevel) {
        val cfg = HexloveServerConfig.config.nature
        for (player in level.players()) {
            if (!ChimeraNatures.isFerocious(player)) continue

            // Idle exhaustion — 10 ticks worth of passive drain, multiplied by MixinNatureExhaustion.
            // The doubling by ferociousExhaustionMultiplier is intentional: idle drain stacks with
            // the active-metabolism multiplier to simulate a ferocious body always burning fuel.
            val idleDrain = (cfg.ferociousIdleExhaustion * 10).toFloat()
            if (idleDrain > 0f) {
                player.causeFoodExhaustion(idleDrain)
            }

            // Golem aggro within 16 blocks.
            val golemBox = player.boundingBox.inflate(16.0)
            val golems = level.getEntitiesOfClass(Mob::class.java, golemBox) { mob ->
                mob.isAlive && (mob is IronGolem || mob is SnowGolem)
            }
            for (golem in golems) {
                golem.target = player
            }
        }
    }
}
