package io.github.teekas.hexlove

import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import dev.architectury.event.EventResult
import dev.architectury.event.events.common.EntityEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.event.events.common.TickEvent
import io.github.teekas.hexlove.behavior.BondTicker
import io.github.teekas.hexlove.nature.ChimeraNatures
import io.github.teekas.hexlove.nature.NatureGate
import io.github.teekas.hexlove.nature.NatureTicker
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.bond.hasBonds
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.damage.DamageInterceptor
import io.github.teekas.hexlove.fx.BondFx
import io.github.teekas.hexlove.marriage.MarriageManager
import io.github.teekas.hexlove.networking.BondSync
import io.github.teekas.hexlove.world.HexloveWorldData
import io.github.teekas.hexlove.world.DebtLedger
import io.github.teekas.hexlove.worldsoul.WorldAffection
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer

/** The single place this mod hooks into the game loop. */
object HexloveEvents {
    fun init() {
        // Persist inventory and optional accessory-slot rings every tick, so a discarded ring cannot
        // be made active again by logging out between long polling gaps.
        TickEvent.SERVER_POST.register { server ->
            MarriageManager.refresh(server)
        }

        TickEvent.SERVER_LEVEL_POST.register { level ->
            try {
                BondTicker.tick(level)
            } catch (t: Throwable) {
                Hexlove.LOGGER.error("Error while ticking bonds in {}", level.dimension().location(), t)
            }
            NatureTicker.tick(level)
        }

        // Requirement 3.3: an entity that loads with persisted bonds gets its behavior back
        EntityEvent.ADD.register { entity, _ ->
            if (entity is net.minecraft.world.entity.LivingEntity) {
                try {
                    DebtLedger.settleOnLoad(entity)
                } catch (t: Throwable) {
                    Hexlove.LOGGER.error("Error while settling a tithe debt for {}", entity.type.description.string, t)
                }
            }
            if (entity.hasBonds) {
                try {
                    BondStore.restoreBehavior(entity)
                } catch (t: Throwable) {
                    Hexlove.LOGGER.error("Error while restoring bonds of {}", entity.type.description.string, t)
                }
            }
            EventResult.pass()
        }

        // Requirement 3.12: an entity that dies for good leaves every harem
        EntityEvent.LIVING_DEATH.register { entity, source ->
            try {
                WorldAffection.onHostileKilled(entity, source)
            } catch (t: Throwable) {
                Hexlove.LOGGER.error("Error while gathering world affection from a hostile kill", t)
            }
            // Stage 4: ferocious kill heal — chance to heal the ferocious player on kill.
            try {
                val killer = source.entity as? ServerPlayer
                if (killer != null && ChimeraNatures.isFerocious(killer)) {
                    val cfg = HexloveServerConfig.config.nature
                    if (killer.random.nextDouble() < cfg.ferociousKillHealChance) {
                        killer.heal(1.0f)
                        // Dark-red conjure particles at the player.
                        val level = killer.serverLevel()
                        level.sendParticles(
                            ConjureParticleOptions(0x8B0000),
                            killer.x, killer.y + killer.bbHeight * 0.5, killer.z,
                            8, 0.3, 0.3, 0.3, 0.05,
                        )
                    }
                }
            } catch (t: Throwable) {
                Hexlove.LOGGER.error("Error in ferocious kill heal", t)
            }
            (entity.level() as? ServerLevel)?.server?.let { server ->
                // A debt is tied to an entity identity, not to whether it was a mob or a player.
                // Death by another cause cannot leave a payment waiting for a future clone/load.
                HexloveWorldData.get(server).dropDebt(entity.uuid)
            }
            if (entity !is ServerPlayer) {
                (entity.level() as? ServerLevel)?.server?.let { server ->
                    HexloveWorldData.get(server).apply {
                        forgetEntity(entity.uuid)
                    }
                }
                if (entity.hasBonds) BondStore.clearAll(entity, io.github.teekas.hexlove.bond.ClearReason.DEATH)
            }
            EventResult.pass()
        }

        // Requirements 7.17, 8.7, 9.3, 16.8, 21.9
        EntityEvent.LIVING_HURT.register { entity, source, amount ->
            // Stage 4: when a ferocious player damages a mob that the neutrality veto protects them
            // from, that mob remembers the player permanently (until it unloads) as a grudge.
            // This is done before DamageInterceptor so the grudge is recorded even if the hit is
            // also processed for bond rules.
            try {
                val attacker = source.entity as? ServerPlayer
                if (attacker != null && ChimeraNatures.isFerocious(attacker) &&
                    NatureGate.isFerociousPackTarget(entity)
                ) {
                    NatureGate.recordGrudge(entity, attacker.uuid)
                }
            } catch (t: Throwable) {
                Hexlove.LOGGER.error("Error while recording nature grudge", t)
            }
            val cancel = try {
                DamageInterceptor.onHurt(entity, source, amount)
            } catch (t: Throwable) {
                Hexlove.LOGGER.error("Error while intercepting damage", t)
                false
            }
            if (cancel) EventResult.interruptFalse() else EventResult.pass()
        }

        // no half-finished light show may hold on to a level that is going away
        LifecycleEvent.SERVER_STOPPING.register { BondFx.clear() }

        PlayerEvent.PLAYER_JOIN.register { player ->
            BondSync.sync(player)
            if (player.hasBonds) BondStore.restoreBehavior(player)
        }

        PlayerEvent.PLAYER_RESPAWN.register { player, _ ->
            BondSync.sync(player)
        }

        PlayerEvent.CHANGE_DIMENSION.register { player, _, _ ->
            BondSync.sync(player)
        }
    }
}
