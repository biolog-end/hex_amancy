package io.github.teekas.hexlove.charm

import io.github.teekas.hexlove.config.HexloveServerConfig

/** Pure plan of one breeding attempt (task 17.1). */
data class BreedPlan(val offspring: Int, val cooldownTicks: Int)

data class BreedingConfig(
    val minOffspring: Int,
    val maxOffspring: Int,
    val maxOffspringPerCast: Int,
    val breedCooldownTicks: Int,
) {
    companion object {
        fun fromConfig(cfg: HexloveServerConfig.Breeding = HexloveServerConfig.config.breeding) = BreedingConfig(
            minOffspring = cfg.minOffspring,
            maxOffspring = cfg.maxOffspring,
            maxOffspringPerCast = cfg.maxOffspringPerCast,
            breedCooldownTicks = cfg.breedCooldownTicks,
        )
    }
}

object BreedingPlanner {
    /**
     * Requirements 11.2 - 11.4: a willing pair gives a litter, an unwilling one gives a single calf,
     * and both parents pay the cooldown either way. Nothing ever exceeds the per-cast limit.
     */
    fun plan(bothReady: Boolean, cfg: BreedingConfig, roll: (Int, Int) -> Int): BreedPlan {
        val count = if (bothReady) {
            roll(cfg.minOffspring, cfg.maxOffspring.coerceAtLeast(cfg.minOffspring))
        } else {
            1
        }
        return BreedPlan(count.coerceIn(1, cfg.maxOffspringPerCast.coerceAtLeast(1)), cfg.breedCooldownTicks)
    }

    /** Requirement 13.3: undead pairs always produce exactly one child. */
    fun undeadPlan(cfg: BreedingConfig): BreedPlan = BreedPlan(1, cfg.breedCooldownTicks)
}
