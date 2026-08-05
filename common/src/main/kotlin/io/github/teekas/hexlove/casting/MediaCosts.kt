package io.github.teekas.hexlove.casting

import at.petrak.hexcasting.api.misc.MediaConstants
import io.github.teekas.hexlove.charm.CharmOutcome
import io.github.teekas.hexlove.config.HexloveServerConfig

/**
 * Task 8.8: every price lives in the config in *amethyst dust units* and is translated to media
 * here, in one place. Pure: same numbers in, same number out, no world access.
 */
object MediaCosts {
    fun dustToMedia(dust: Double): Long {
        if (dust.isNaN()) return 0L
        val media = dust * MediaConstants.DUST_UNIT
        // an astronomically stubborn heart must not wrap around into a bargain
        return if (media >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE else media.toLong().coerceAtLeast(0L)
    }

    private val costs get() = HexloveServerConfig.config.costs

    /**
     * Requirement 7.12, revised: strength up to the soft cap is cheap and linear, which is where
     * every ordinary creature lives. Past it every further point multiplies the surcharge, so
     * charming a boss is arithmetically possible and practically a creative-mode stunt.
     */
    fun charmDust(power: Double, base: Double, perPower: Double, softCap: Double, growth: Double): Double {
        if (!power.isFinite() || power <= 0.0) return base
        val linear = base + perPower * minOf(power, softCap)
        if (power <= softCap) return linear
        val excess = power - softCap
        val surcharge = base * (Math.pow(growth.coerceAtLeast(1.0), excess) - 1.0)
        return if (surcharge.isFinite()) linear + surcharge else Double.MAX_VALUE
    }

    /** Requirements 7.10 - 7.12: the price follows the outcome, not the target's mood. */
    fun cupidsCharm(outcome: CharmOutcome, power: Double): Long {
        val c = costs
        val dust = when (outcome) {
            is CharmOutcome.Breed, CharmOutcome.Chimera, CharmOutcome.ZombieBreed -> c.cupidsCharmBreeding
            CharmOutcome.ZombieVillagerBreed -> c.cupidsCharmZombieVillager
            else -> charmDust(
                power,
                c.cupidsCharmBase,
                c.cupidsCharmPerPower,
                c.cupidsCharmPowerSoftCap,
                c.cupidsCharmPowerGrowth,
            )
        }
        return dustToMedia(dust)
    }

    /** Requirement 14.8: negligible, it only looks. */
    fun matchmakersPurification(): Long = dustToMedia(costs.matchmakersPurification)

    /** Requirement 15.7: the same price for every branch, so the price leaks nothing. */
    fun animalPassion(): Long = dustToMedia(costs.animalPassion)

    fun breathExchange(): Long = dustToMedia(costs.breathExchange)

    fun weaversPurification(): Long = dustToMedia(costs.weaversPurification)

    fun heartbreak(): Long = dustToMedia(costs.heartbreak)

    fun blindingJealousy(seconds: Double): Long =
        dustToMedia(costs.blindingJealousyBase + costs.blindingJealousyPerSecond * seconds.coerceAtLeast(0.0))

    fun sirensSong(seconds: Double): Long =
        dustToMedia(costs.sirensSongBase + costs.sirensSongPerSecond * seconds.coerceAtLeast(0.0))

    fun pheromoneMist(radius: Double, seconds: Double): Long =
        dustToMedia(
            costs.pheromoneMistBase +
                costs.pheromoneMistPerBlock * radius.coerceAtLeast(0.0) +
                costs.pheromoneMistPerSecond * seconds.coerceAtLeast(0.0),
        )

    fun phantomIdeal(seconds: Double): Long =
        dustToMedia(costs.phantomIdealBase + costs.phantomIdealPerSecond * seconds.coerceAtLeast(0.0))

    fun succubusTithe(): Long = dustToMedia(costs.succubusTithe)

    fun soulbind(): Long = dustToMedia(costs.soulbind)

    fun heartsReflection(): Long = dustToMedia(costs.heartsReflection)
}
