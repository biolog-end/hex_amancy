package io.github.teekas.hexlove.fabric.compat

import dev.emi.trinkets.api.TrinketsApi
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.compat.AccessoryRingAccess
import io.github.teekas.hexlove.registry.HexloveItems

/** Optional Trinkets 3.7.2 bridge for the standard hand/ring and offhand/ring slots. */
object FabricTrinketsCompat {
    private const val RING_SLOT = "ring"

    fun init() {
        AccessoryRingAccess.install { player ->
            val component = TrinketsApi.getTrinketComponent(player).orElse(null)
                ?: return@install emptyList()
            component.getEquipped(HexloveItems.SOULBOUND_RING.value)
                .asSequence()
                .filter { equipped ->
                    val type = equipped.a.inventory.slotType
                    type.name == RING_SLOT && (type.group == "hand" || type.group == "offhand")
                }
                .map { equipped ->
                    AccessoryRingAccess.EquippedRing(equipped.b) {
                        equipped.a.inventory.markUpdate()
                    }
                }
                .toList()
        }
        Hexlove.LOGGER.info("Enabled Trinkets 3.7.2 Soulbound Ring compatibility")
    }
}
