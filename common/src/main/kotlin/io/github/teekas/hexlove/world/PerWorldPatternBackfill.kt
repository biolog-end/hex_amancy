package io.github.teekas.hexlove.world

import at.petrak.hexcasting.api.casting.math.EulerPathFinder
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.mod.HexTags
import at.petrak.hexcasting.api.utils.isOfTag
import at.petrak.hexcasting.server.ScrungledPatternsSave
import at.petrak.hexcasting.xplat.IXplatAbstractions
import io.github.teekas.hexlove.Hexlove

/**
 * Gives per-world patterns a scrungled shape in worlds that were created before the pattern existed.
 *
 * Hex builds its table of Great Spell shapes once, in `ScrungledPatternsSave.createFromScratch`, and
 * the loader for an existing world only reads back what was written then. A Great Spell added to an
 * old world therefore has no shape in it: casting it is impossible, and an Ancient Scroll rolled for
 * it crashes the server on the next inventory tick, because `ItemScroll.inventoryTick` dereferences
 * the missing pattern without a null check.
 *
 * So we walk the action registry once per loaded save and mint a shape for every per-world pattern
 * that has none. Entries already in the table are never touched — regenerating them would move
 * Great Spells the players of that world have already learned.
 */
object PerWorldPatternBackfill {
    /** Fresh seeds to try if the drawing we get back collides with a signature already in use. */
    private const val SEED_ATTEMPTS = 8

    /**
     * Called from the mixin at the end of every `ScrungledPatternsSave.open`, which is often (every
     * cast, every tick of a scroll waiting to resolve), so everything past the flag check has to be
     * skipped on all but the first call for a given save.
     */
    @JvmStatic
    fun ensure(save: ScrungledPatternsSave?, seed: Long) {
        if (save == null) return

        val access = save as? PerWorldPatternAccess
        if (access == null) {
            Hexlove.LOGGER.error(
                "Hex's per-world pattern table is not accessible, so missing Great Spell shapes cannot be " +
                    "added to this world. Great Spells registered after the world was created will not be " +
                    "castable here."
            )
            return
        }

        if (access.hexloveBackfilled()) return
        // Marked up front: one failed attempt should not turn into one failed attempt per tick.
        access.hexloveMarkBackfilled()

        try {
            backfill(save, access, seed)
        } catch (t: Throwable) {
            Hexlove.LOGGER.error(
                "Failed to add missing Great Spell shapes to this world. Great Spells registered after the " +
                    "world was created will not be castable here.",
                t,
            )
        }
    }

    private fun backfill(save: ScrungledPatternsSave, access: PerWorldPatternAccess, seed: Long) {
        val registry = IXplatAbstractions.INSTANCE.actionRegistry
        val lookup = access.hexloveLookup()
        val reverseLookup = access.hexloveReverseLookup()
        var added = 0

        for (key in registry.registryKeySet()) {
            if (reverseLookup.containsKey(key)) continue
            if (!isOfTag(registry, key, HexTags.Actions.PER_WORLD_PATTERN)) continue

            val prototype = registry.get(key)?.prototype ?: continue
            val scrungled = scrungle(prototype, seed, lookup.keys)
            if (scrungled == null) {
                Hexlove.LOGGER.error(
                    "Could not find a free shape for the Great Spell {} in this world; it stays uncastable here.",
                    key.location(),
                )
                continue
            }

            val signature = scrungled.anglesSignature()
            lookup[signature] = ScrungledPatternsSave.PerWorldEntry(key, scrungled.startDir)
            reverseLookup[key] = signature
            added++
            Hexlove.LOGGER.info("Gave the Great Spell {} a shape in this world, which had none.", key.location())
        }

        if (added > 0) {
            save.setDirty()
        }
    }

    /**
     * [EulerPathFinder.findAltDrawing] re-routes the prototype over the same edges, so the drawing
     * keeps its silhouette. Its rule already rejects taken signatures for a hundred walks; if it
     * gives up anyway it hands back the prototype, and we try again from a different seed.
     */
    private fun scrungle(prototype: HexPattern, seed: Long, taken: Set<String>): HexPattern? {
        var attemptSeed = seed
        repeat(SEED_ATTEMPTS) {
            val candidate = EulerPathFinder.findAltDrawing(prototype, attemptSeed) {
                !taken.contains(it.anglesSignature())
            }
            if (!taken.contains(candidate.anglesSignature())) return candidate
            attemptSeed = attemptSeed * 6364136223846793005L + 1442695040888963407L
        }
        return null
    }
}
