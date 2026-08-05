package io.github.teekas.hexlove.forge

import io.github.teekas.hexlove.HexloveClient
import io.github.teekas.hexlove.forge.compat.ForgeCuriosClientCompat
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import thedarkcolour.kotlinforforge.forge.LOADING_CONTEXT

object ForgeHexloveClient {
    @Suppress("UNUSED_PARAMETER")
    fun init(event: FMLClientSetupEvent) {
        HexloveClient.init()
        if (ModList.get().isLoaded("curios")) ForgeCuriosClientCompat.init()
        LOADING_CONTEXT.registerExtensionPoint(ConfigScreenFactory::class.java) {
            ConfigScreenFactory { _, parent -> HexloveClient.getConfigScreen(parent) }
        }
    }
}
