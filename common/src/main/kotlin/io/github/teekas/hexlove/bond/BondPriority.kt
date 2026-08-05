package io.github.teekas.hexlove.bond

/** Requirement 4.1: which state wins when several are present. Pure, order-of-application independent. */
enum class DominantEffect { NONE, JEALOUSY, LOVE, MARRIAGE, HEARTBREAK }

object BondPriority {
    fun dominant(
        heartbroken: Boolean,
        married: Boolean,
        hasLove: Boolean,
        hasJealousy: Boolean,
    ): DominantEffect = when {
        heartbroken -> DominantEffect.HEARTBREAK
        married -> DominantEffect.MARRIAGE
        hasLove -> DominantEffect.LOVE
        hasJealousy -> DominantEffect.JEALOUSY
        else -> DominantEffect.NONE
    }
}

/** Requirements 4.5, 8.6, 16.3: pure targeting veto. */
object BondRules {
    /**
     * Two bans, in this order:
     *
     * 1. A creature may never land a direct hit on the owner of its own Love_Bond. Jealousy never
     *    lifts this one.
     * 2. Two creatures charmed onto the *same* master do not fight each other — a harem is not a
     *    brawl. Jealousy *does* lift this one: a jealous creature strikes everybody who comes near
     *    the one it guards, harem or not.
     */
    fun forbidsTargeting(
        attackerLoveTarget: java.util.UUID?,
        attackerIsJealous: Boolean,
        candidateLoveTarget: java.util.UUID?,
        candidate: java.util.UUID,
    ): Boolean {
        if (attackerLoveTarget == null) return false
        if (attackerLoveTarget == candidate) return true
        if (attackerIsJealous) return false
        return candidateLoveTarget != null && candidateLoveTarget == attackerLoveTarget
    }
}
