package io.github.teekas.hexlove.fabric.compat

import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.compat.CarriedEntityAccess
import net.fabricmc.loader.api.FabricLoader
import tschipp.carryon.common.carry.CarryOnDataManager

/**
 * Carry On 2.1.2.7 stores a carried entity's full NBT in the carrying player's data. Reading the
 * stored UUID lets the common love logic treat a held beloved as being at its carrier, without
 * materialising a second entity from that NBT.
 */
object FabricCarryOnCompat {
    private const val CARRY_ON_ID = "carryon"
    private const val ENTITY_UUID_KEY = "UUID"

    fun init() {
        if (!FabricLoader.getInstance().isModLoaded(CARRY_ON_ID)) return

        CarriedEntityAccess.install { player, entity ->
            val carriedNbt = CarryOnDataManager.getCarryData(player).contentNbt ?: return@install false
            carriedNbt.hasUUID(ENTITY_UUID_KEY) && carriedNbt.getUUID(ENTITY_UUID_KEY) == entity
        }
        Hexlove.LOGGER.info("Enabled Carry On 2.1.2.7 compatibility")
    }
}
