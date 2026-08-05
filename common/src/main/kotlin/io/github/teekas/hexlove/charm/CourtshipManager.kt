package io.github.teekas.hexlove.charm

import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.bond.CourtshipKind
import io.github.teekas.hexlove.bond.CourtshipState
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.compat.CarriedEntityAccess
import io.github.teekas.hexlove.sameLevelLiving
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal

/**
 * Requirement 11, revised after playtesting: a charm between two breedable creatures no longer
 * conjures children out of thin air across the map. It starts a *courtship*.
 *
 * The two walk to each other, trailing hearts, stand together for a while, and only then is there a
 * litter — exactly the shape of vanilla breeding, only with more children and no wheat. Distance
 * matters again, and interrupting the pair interrupts the result.
 */
object CourtshipManager {
    /** Both sides get the state; only the subject leads, so the litter can never be spawned twice. */
    fun begin(level: ServerLevel, subject: Entity, master: Entity, kind: CourtshipKind, offspring: Int) {
        val cfg = HexloveServerConfig.config.breeding
        val expiresAt = level.gameTime + cfg.courtshipTimeoutTicks

        // Being charmed clears an adult's leftover breeding cooldown. Childhood is a negative age,
        // so assigning zero unconditionally would act as an instant growth hormone.
        (subject as? Animal)?.takeIf { it.age > 0 }?.age = 0
        (master as? Animal)?.takeIf { it.age > 0 }?.age = 0

        BondStore.setCourtship(
            subject,
            CourtshipState(master.uuid, kind, offspring, expiresAt, leader = true),
        )
        BondStore.setCourtship(
            master,
            CourtshipState(subject.uuid, kind, offspring, expiresAt, leader = false),
        )

        BreedingExecutor.hearts(level, subject)
        BreedingExecutor.hearts(level, master)
    }

    /**
     * Called from the bond ticker every [interval] ticks. Only the leader does any bookkeeping; the
     * other side just needs its own copy of the state so its behavior survives a reload.
     */
    fun tick(level: ServerLevel, entity: Entity, now: Long, interval: Int) {
        val state = BondStore.courtship(entity) ?: return

        if (!state.leader) {
            // a follower whose leader vanished must not court forever
            if (now >= state.expiresAt) BondStore.clearCourtship(entity)
            return
        }

        val mate = entity.sameLevelLiving(state.mate)
        if (mate == null) {
            val mateIsCarried = CarriedEntityAccess.carrierOf(level.server, state.mate) != null
            // Carry On preserves the mate's NBT and UUID, but it is temporarily not a world entity.
            // Pause the meeting until it is placed again; the normal absolute timeout still applies.
            if (now >= state.expiresAt || !mateIsCarried) abandon(level, entity, null)
            return
        }
        val mutual = BondStore.courtship(mate)?.mate == entity.uuid
        if (now >= state.expiresAt || !mate.isAlive || !mutual) {
            abandon(level, entity, mate)
            return
        }

        val cfg = HexloveServerConfig.config.breeding
        BreedingExecutor.hearts(level, entity)
        BreedingExecutor.hearts(level, mate)

        val contact = cfg.courtshipContactDistance
        if (entity.distanceToSqr(mate) <= contact * contact) {
            val together = state.contactTicks + interval
            if (together >= cfg.courtshipContactTicks) {
                consummate(level, entity, mate, state)
            } else {
                BondStore.updateCourtship(entity, state.copy(contactTicks = together))
            }
        } else if (state.contactTicks > 0) {
            // they drifted apart again: the clock restarts
            BondStore.updateCourtship(entity, state.copy(contactTicks = 0))
        }
    }

    private fun consummate(level: ServerLevel, a: Entity, b: LivingEntity, state: CourtshipState) {
        BondStore.clearCourtship(a)
        BondStore.clearCourtship(b)

        val plan = BreedPlan(state.offspring, HexloveServerConfig.config.breeding.breedCooldownTicks)
        when (state.kind) {
            CourtshipKind.SAME_SPECIES -> {
                val x = a as? Animal ?: return
                val y = b as? Animal ?: return
                if (x.isBaby || y.isBaby) {
                    abandon(level, x, y)
                    return
                }
                BreedingExecutor.breedAnimals(level, x, y, plan)
            }

            CourtshipKind.CHIMERA -> {
                val x = a as? Animal ?: return
                val y = b as? Animal ?: return
                if (x.isBaby || y.isBaby) {
                    abandon(level, x, y)
                    return
                }
                ChimeraFactory.breed(level, x, y, plan)
            }

            CourtshipKind.ZOMBIE, CourtshipKind.ZOMBIE_VILLAGER -> {
                BreedingExecutor.breedUndead(level, a, b, plan)
            }
        }
    }

    /** Nothing came of it. No cooldown either: the pair was never given the chance. */
    private fun abandon(level: ServerLevel, leader: Entity, mate: LivingEntity?) {
        BondStore.clearCourtship(leader)
        mate?.let(BondStore::clearCourtship)
        level.sendParticles(
            ParticleTypes.SMOKE,
            leader.x, leader.y + leader.bbHeight * 0.75, leader.z,
            5, 0.3, 0.3, 0.3, 0.01,
        )
    }
}
