package io.github.teekas.hexlove.charm

import io.github.teekas.hexlove.bond.LoveBond
import io.github.teekas.hexlove.config.HexloveServerConfig
import kotlin.math.min

/** Everything the charm model needs, so it can be tested without a world. */
data class CharmConfig(
    val resistancePerHealth: Double,
    val playerResistance: Double,
    val permanenceThreshold: Double,
    val durationMultiplierTicks: Long,
    val maxDurationTicks: Long,
    val maxPlayerDurationTicks: Long,
) {
    companion object {
        fun fromConfig(cfg: HexloveServerConfig.Charm = HexloveServerConfig.config.charm) = CharmConfig(
            resistancePerHealth = cfg.resistancePerHealth,
            playerResistance = cfg.playerResistance,
            permanenceThreshold = cfg.permanenceThreshold,
            durationMultiplierTicks = cfg.durationMultiplierTicks,
            maxDurationTicks = cfg.maxDurationTicks,
            maxPlayerDurationTicks = cfg.maxPlayerDurationTicks,
        )
    }
}

/** How far a given strength of feeling can reach at all, in health. Pure. */
data class CharmReach(
    val bossHealthThreshold: Double,
    val charmableHealthBase: Double,
    val charmableHealthPerPower: Double,
    val softCap: Double,
) {
    /** Requirement 7, revised: past the soft cap, extra strength buys a little more reach. */
    fun charmableHealth(power: Double): Double {
        if (!power.isFinite()) return charmableHealthBase
        val excess = (power - softCap).coerceAtLeast(0.0)
        return charmableHealthBase + charmableHealthPerPower * excess
    }

    /** A heart no feeling reaches, at any price. */
    fun isBeyondLove(maxHealth: Double): Boolean = maxHealth >= bossHealthThreshold

    companion object {
        fun fromConfig(cfg: HexloveServerConfig.ServerConfig = HexloveServerConfig.config) = CharmReach(
            bossHealthThreshold = cfg.charm.bossHealthThreshold,
            charmableHealthBase = cfg.charm.charmableHealthBase,
            charmableHealthPerPower = cfg.charm.charmableHealthPerPower,
            softCap = cfg.costs.cupidsCharmPowerSoftCap,
        )
    }
}

sealed interface CharmResult {
    val ticks: Long

    data object Permanent : CharmResult {
        override val ticks: Long get() = LoveBond.PERMANENT
    }

    data class Temporary(override val ticks: Long) : CharmResult
}

/**
 * Requirement 7: the third iota is *strength of feeling*, not a duration. Duration comes out of the
 * ratio between that strength and how stubborn the target's heart is.
 */
object CharmModel {
    /** Requirements 7.2, 7.3. */
    fun resistance(maxHealth: Double, isPlayer: Boolean, cfg: CharmConfig): Double =
        if (isPlayer) cfg.playerResistance else (maxHealth * cfg.resistancePerHealth).coerceAtLeast(0.001)

    /** Requirements 7.4 - 7.7, 9.1: a player never becomes permanent, no matter the power. */
    fun resolve(power: Double, resistance: Double, isPlayer: Boolean, cfg: CharmConfig): CharmResult {
        val safeResistance = resistance.coerceAtLeast(0.001)
        if (!isPlayer && power >= safeResistance * cfg.permanenceThreshold) return CharmResult.Permanent

        val cap = if (isPlayer) cfg.maxPlayerDurationTicks else cfg.maxDurationTicks
        val raw = (power / safeResistance) * cfg.durationMultiplierTicks
        val ticks = min(raw.toLong(), cap).coerceAtLeast(1L)
        return CharmResult.Temporary(ticks)
    }

    /** Requirement 7.8: non-positive or non-numeric strength is rejected before the model runs. */
    fun isValidPower(power: Double): Boolean = power.isFinite() && power > 0.0
}
