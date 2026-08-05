package io.github.teekas.hexlove.nature

import java.util.UUID

/**
 * Transient per-server record of the last game-time a serene player ate near an adult farm animal.
 *
 * State is never serialized — intentionally lost on restart, matching the mod's "AI is not saved"
 * philosophy (see BehaviorTracker documentation).
 *
 * Written by [NatureHooks.recordMealNearAnimal]; read by [NatureHooks.onAnimalAdvance].
 */
object MealMemory {
    // Keyed by player UUID; value is the gameTime of the most recent qualifying meal.
    private val lastMealTime = HashMap<UUID, Long>()

    fun record(playerUUID: UUID, gameTime: Long) {
        lastMealTime[playerUUID] = gameTime
    }

    fun get(playerUUID: UUID): Long = lastMealTime[playerUUID] ?: -1L

    fun remove(playerUUID: UUID) {
        lastMealTime.remove(playerUUID)
    }
}
