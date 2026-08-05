package io.github.teekas.hexlove.bond

import java.util.UUID

/**
 * Persisted states of one entity. Expiry is stored as an absolute world tick, so an unloaded entity
 * still "runs out" while the world ticks on (see design: `expiresAt`, not `remaining`).
 */
data class LoveBond(
    val target: UUID,
    val expiresAt: Long,
    val permanent: Boolean = expiresAt == PERMANENT,
) {
    companion object {
        /** Requirement 7.4: a permanent bond is just an expiry that never comes. */
        const val PERMANENT: Long = Long.MAX_VALUE
    }
}

/** A saved harem entry must be able to reject a temporary bond even while its owner is unloaded. */
fun LoveBond.isActiveAt(gameTime: Long): Boolean = expiresAt > gameTime

data class JealousyBond(
    val target: UUID,
    val expiresAt: Long,
    /** Transient: last tick the jealous entity hurt somebody near the protected one (Requirement 16.6). */
    val lastAttackTick: Long = 0,
)

data class HeartbreakState(val expiresAt: Long)

data class SirenState(val caller: UUID, val expiresAt: Long)

/** A gentle field that interrupts a hostile creature's will to fight until it is hurt again. */
data class PheromoneState(val expiresAt: Long)

/** A temporary focal point that draws a creature's attention and movement without becoming its target. */
data class PhantomIdealState(val ideal: UUID, val expiresAt: Long)

/** What a finished courtship produces. */
enum class CourtshipKind { SAME_SPECIES, CHIMERA, ZOMBIE, ZOMBIE_VILLAGER }

/**
 * Requirement 11, revised: a charm between two breedable creatures is a *betrothal*, not a birth.
 * Both walk to each other, stand together for a while, and only then are there children — the way
 * vanilla does it. Nothing happens at a distance.
 */
data class CourtshipState(
    val mate: UUID,
    val kind: CourtshipKind,
    val offspring: Int,
    val expiresAt: Long,
    /** Exactly one of the pair carries the bookkeeping, so the litter is never spawned twice. */
    val leader: Boolean,
    val contactTicks: Int = 0,
)

/** Why a bond was cleared; used for messaging and behavior teardown. */
enum class ClearReason { EXPIRED, HEARTBREAK, REPLACED, TARGET_GONE, DEATH }

/**
 * Task 3.3 / Requirements 16.2, 18.2, 20.3: a duration given in seconds becomes an absolute expiry,
 * capped by the configured maximum and always strictly in the future.
 */
object Durations {
    fun expiryFromSeconds(seconds: Double, now: Long, maxTicks: Long): Long {
        val requested = if (seconds.isNaN()) 1L else (seconds * 20.0).toLong()
        val ticks = requested.coerceIn(1L, maxTicks.coerceAtLeast(1L))
        return now + ticks
    }
}
