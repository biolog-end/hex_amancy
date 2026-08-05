package io.github.teekas.hexlove.forge.compat

import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.compat.AccessoryRingAccess
import io.github.teekas.hexlove.registry.HexloveItems
import top.theillusivec4.curios.api.CuriosApi

/** Optional Curios 5.14.1 bridge for a single standard ring slot. */
object ForgeCuriosCompat {
    private const val RING_SLOT = "ring"

    fun init() {
        AccessoryRingAccess.install { player ->
            val handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null)
                ?: return@install emptyList()
            handler.findCurios(HexloveItems.SOULBOUND_RING.value)
                .asSequence()
                .filter { result ->
                    val context = result.slotContext()
                    context.identifier() == RING_SLOT && !context.cosmetic()
                }
                .map { result ->
                    val context = result.slotContext()
                    AccessoryRingAccess.EquippedRing(result.stack()) {
                        handler.setEquippedCurio(context.identifier(), context.index(), result.stack())
                    }
                }
                .toList()
        }
        Hexlove.LOGGER.info("Enabled Curios 5.14.1 Soulbound Ring compatibility")
    }
}
