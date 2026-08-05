package io.github.teekas.hexlove.bond

import net.minecraft.world.entity.Entity

/**
 * Duck interface implemented by `MixinEntityBondStorage` on every [Entity].
 * This is the cross-loader way to store data on entities: Cardinal Components is Fabric-only and
 * `getPersistentData()` is Forge-only, but a common mixin works on both.
 */
interface BondHolder {
    /** Never null; created lazily on first access. */
    fun hexloveBonds(): BondData

    /** Cheap check that does not allocate. */
    fun hexloveHasBonds(): Boolean
}

val Entity.bonds: BondData
    get() = (this as BondHolder).hexloveBonds()

val Entity.hasBonds: Boolean
    get() = (this as BondHolder).hexloveHasBonds()
