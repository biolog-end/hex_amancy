package io.github.teekas.hexlove.world

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.server.ScrungledPatternsSave
import net.minecraft.resources.ResourceKey

/**
 * Access to the two private tables inside [ScrungledPatternsSave], implemented by
 * `MixinPerWorldPatternBackfill`.
 *
 * Hex fills those tables exactly once, when the world is created, so a per-world pattern registered
 * afterwards — by us, or by any addon installed into an existing world — is missing from them
 * forever. [PerWorldPatternBackfill] is what fills the gaps; this is how it reaches them.
 */
interface PerWorldPatternAccess {
    /** Scrungled angle signature -> entry. */
    fun hexloveLookup(): MutableMap<String, ScrungledPatternsSave.PerWorldEntry>

    /** Action key -> scrungled angle signature. */
    fun hexloveReverseLookup(): MutableMap<ResourceKey<ActionRegistryEntry>, String>

    /** True once this instance has been walked over, so `open()` stays cheap on every later call. */
    fun hexloveBackfilled(): Boolean

    fun hexloveMarkBackfilled()
}
