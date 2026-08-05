package io.github.teekas.hexlove.casting.tithe

import java.util.UUID

/** A world-free snapshot. It is deliberately only accounting data, never a live entity reference. */
data class TitheSource(
    val entity: UUID,
    val health: Double,
    val existingDebt: Double,
    val loaded: Boolean,
    val distanceSqr: Double,
)

data class TithePortion(val entity: UUID, val amount: Double)

data class TithePlan(val portions: List<TithePortion>) {
    val total: Double = portions.sumOf(TithePortion::amount)
}

/** The accounting part of Succubus' Tithe. It has no Minecraft objects and is safe to test alone. */
object TithePlanner {
    fun distribute(
        requested: Double,
        sources: Iterable<TitheSource>,
    ): TithePlan {
        var remaining = requested.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0
        if (remaining <= 0.0) return TithePlan(emptyList())

        // A tithe is shared by every willing heart. This is a water-fill: everyone pays the same
        // share, then a weak source is exhausted and the small remainder is divided evenly amongst
        // those still able to pay. Existing deferred damage is already reserved against a source's
        // health, but does not make the rest of that health disappear from the next calculation.
        val available = sources
            .associate { it.entity to (it.health - it.existingDebt).coerceAtLeast(0.0) }
            .filterValues { it > 1.0e-6 }
            .toMutableMap()
        if (available.isEmpty()) return TithePlan(emptyList())

        val paid = available.keys.associateWith { 0.0 }.toMutableMap()
        while (remaining > 1.0e-6 && available.isNotEmpty()) {
            val share = remaining / available.size
            var paidThisRound = 0.0
            val exhausted = ArrayList<UUID>()
            for ((entity, capacity) in available) {
                val portion = minOf(share, capacity)
                paid[entity] = (paid[entity] ?: 0.0) + portion
                available[entity] = capacity - portion
                paidThisRound += portion
                if (capacity - portion <= 1.0e-6) exhausted += entity
            }
            remaining -= paidThisRound
            exhausted.forEach(available::remove)
            if (paidThisRound <= 1.0e-6) break
        }

        return TithePlan(
            paid.entries
                .asSequence()
                .filter { it.value > 1.0e-6 }
                .sortedBy { it.key }
                .map { TithePortion(it.key, it.value) }
                .toList(),
        )
    }
}
