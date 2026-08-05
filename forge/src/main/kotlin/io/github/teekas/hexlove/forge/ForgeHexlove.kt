package io.github.teekas.hexlove.forge

import dev.architectury.platform.forge.EventBuses
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.forge.compat.ForgeCarryOnCompat
import io.github.teekas.hexlove.forge.compat.ForgeCuriosCompat
import io.github.teekas.hexlove.forge.datagen.ForgeHexloveDatagen
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@Mod(Hexlove.MODID)
class ForgeHexlove {
    init {
        MOD_BUS.apply {
            EventBuses.registerModEventBus(Hexlove.MODID, this)
            addListener(::onCommonSetup)
            addListener(ForgeHexloveClient::init)
            addListener(ForgeHexloveDatagen::init)
            addListener(ForgeHexloveServer::init)
        }
        Hexlove.init()
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            Hexlove.initPostRegistry()
            ForgeCarryOnCompat.init()
            if (ModList.get().isLoaded("curios")) ForgeCuriosCompat.init()
        }
    }
}
