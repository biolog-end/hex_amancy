package io.github.teekas.hexlove.bond

import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.marriage.MarriageManager
import io.github.teekas.hexlove.nature.NatureGate
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.monster.Slime

/**
 * Entry point for mixins. Everything here must be cheap and must never throw: it runs inside
 * vanilla code paths that have no idea this mod exists.
 */
object BondGate {
    @JvmStatic
    fun forbidsTargeting(attacker: Entity, candidate: Entity): Boolean {
        // Nature rules are checked first: cheap and cover cases where bond data is absent.
        if (NatureGate.forbidsTargeting(attacker, candidate)) return true

        return try {
            val attackerHolder = attacker as? BondHolder
            val attackerData = if (attackerHolder != null && attackerHolder.hexloveHasBonds()) {
                attackerHolder.hexloveBonds()
            } else {
                null
            }

            // These two effects deliberately leave no exception for a former target: the creature is
            // either serene, or so absorbed by the phantom that nobody else exists to it.
            if (attackerData?.pheromone != null || attackerData?.phantomIdeal != null) {
                true
            } else if (marriageForbidsTargeting(attacker, candidate, attackerData)) {
                true
            } else if (attackerData == null) {
                false
            } else {
                val loveTarget = attackerData.love?.target
                if (loveTarget == null) {
                    false
                } else {
                    val candidateHolder = candidate as? BondHolder
                    val candidateLoveTarget = if (candidateHolder != null && candidateHolder.hexloveHasBonds()) {
                        candidateHolder.hexloveBonds().love?.target
                    } else {
                        null
                    }
                    BondRules.forbidsTargeting(
                        attackerLoveTarget = loveTarget,
                        attackerIsJealous = attackerData.jealousy != null,
                        candidateLoveTarget = candidateLoveTarget,
                        candidate = candidate.uuid,
                    )
                }
            }
        } catch (_: Throwable) {
            // This runs inside vanilla target selection: a stale world or a compatibility issue must
            // leave ordinary mob AI intact rather than taking the server down.
            false
        }
    }

    /**
     * An active vow protects each spouse from the other's pets and admirers. Jealousy is an
     * intentional exception for admirers: a jealous creature is allowed to defend its guarded
     * target even when that target is married. A tame, however, never turns on its owner's spouse.
     */
    private fun marriageForbidsTargeting(
        attacker: Entity,
        candidate: Entity,
        attackerData: BondData?,
    ): Boolean {
        val petOwner = (attacker as? TamableAnimal)?.ownerUUID
        val beloved = attackerData?.love?.target
        if (petOwner == null && beloved == null) return false

        val server = (attacker.level() as? ServerLevel)?.server ?: return false
        if (petOwner != null && MarriageManager.spouseOf(server, petOwner) == candidate.uuid) return true

        beloved ?: return false
        if (attackerData?.jealousy != null) return false
        return MarriageManager.spouseOf(server, beloved) == candidate.uuid
    }

    /** Requirement 18, revised: a broken heart cannot fall in love again, not even with wheat. */
    @JvmStatic
    fun isHeartbroken(entity: Entity): Boolean = try {
        BondStore.isHeartbroken(entity)
    } catch (t: Throwable) {
        false
    }

    /**
     * A slime cut in two leaves two slimes, and what the whole one felt both halves feel. Bonds are
     * per-entity state, so the pieces are born blank unless somebody hands the feeling down.
     *
     * The pieces are already in the level by the time this runs; they are told apart from any slime
     * that happened to be standing nearby by their age, since one that has never been ticked can only
     * have come from this split.
     *
     * Runs inside `Slime.remove`, so it must swallow everything: a failure here would take the removal
     * with it.
     */
    @JvmStatic
    fun inheritOnSplit(parent: Entity) {
        try {
            val level = parent.level() as? ServerLevel ?: return
            val love = BondStore.love(parent)
            val jealousy = BondStore.jealousy(parent)
            if (love == null && jealousy == null) return

            val pieces = level.getEntitiesOfClass(Slime::class.java, parent.boundingBox.inflate(4.0)) {
                it !== parent && it.isAlive && it.tickCount == 0 && !BondStore.isHeartbroken(it)
            }
            for (piece in pieces) {
                love?.let { BondStore.setLove(piece, it) }
                jealousy?.let { BondStore.setJealousy(piece, it) }
            }
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("Failed to pass a bond down to the pieces of a slime", t)
        }
    }
}
