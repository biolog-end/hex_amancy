package io.github.teekas.hexlove.forge.compat

import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.compat.CarriedEntityAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraftforge.fml.ModList

/** Optional Carry On 2.1.2.7 bridge, kept reflective so Forge can start without the mod installed. */
object ForgeCarryOnCompat {
    private const val CARRY_ON_ID = "carryon"
    private const val ENTITY_UUID_KEY = "UUID"

    fun init() {
        if (!ModList.get().isLoaded(CARRY_ON_ID)) return

        runCatching {
            val dataClass = Class.forName("tschipp.carryon.common.carry.CarryOnData")
            val managerClass = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager")
            val getCarryData = managerClass.getMethod("getCarryData", Player::class.java)
            val getContentNbt = dataClass.getMethod("getContentNbt")

            CarriedEntityAccess.install { player, entity ->
                runCatching {
                    val carryData = getCarryData.invoke(null, player)
                    val carriedNbt = getContentNbt.invoke(carryData) as? CompoundTag
                    carriedNbt != null && carriedNbt.hasUUID(ENTITY_UUID_KEY) &&
                        carriedNbt.getUUID(ENTITY_UUID_KEY) == entity
                }.getOrDefault(false)
            }
            Hexlove.LOGGER.info("Enabled Carry On Forge 2.1.2.7 compatibility")
        }.onFailure { error ->
            Hexlove.LOGGER.warn("Carry On is installed, but its 2.1.2.7 data API could not be linked", error)
        }
    }
}
