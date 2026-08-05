package io.github.teekas.hexlove.compat

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/**
 * Loader-neutral view of Soulbound Rings equipped through an optional accessory mod.
 *
 * Platform adapters install a lookup only when their accessory API is present. The callback on an
 * entry must be invoked after changing its stack NBT so the owning capability/component can sync
 * the new ring state to the client.
 */
object AccessoryRingAccess {
    class EquippedRing(
        val stack: ItemStack,
        private val update: () -> Unit,
    ) {
        fun markChanged() = update()
    }

    private var lookup: ((ServerPlayer) -> List<EquippedRing>)? = null

    fun install(equippedRings: (ServerPlayer) -> List<EquippedRing>) {
        lookup = equippedRings
    }

    fun equippedRings(player: ServerPlayer): List<EquippedRing> =
        lookup?.invoke(player).orEmpty()
}
