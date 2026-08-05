package io.github.teekas.hexlove.fabric

import io.github.teekas.hexlove.HexloveClient
import io.github.teekas.hexlove.fabric.compat.FabricTrinketsClientCompat
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader

object FabricHexloveClient : ClientModInitializer {
    override fun onInitializeClient() {
        HexloveClient.init()
        if (FabricLoader.getInstance().isModLoaded("trinkets")) FabricTrinketsClientCompat.init()
    }
}
