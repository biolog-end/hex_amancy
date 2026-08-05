package io.github.teekas.hexlove.fabric

import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.fabric.compat.FabricCarryOnCompat
import io.github.teekas.hexlove.fabric.compat.FabricTrinketsCompat
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader

object FabricHexlove : ModInitializer {
    override fun onInitialize() {
        Hexlove.init()
        Hexlove.initPostRegistry()
        FabricCarryOnCompat.init()
        if (FabricLoader.getInstance().isModLoaded("trinkets")) FabricTrinketsCompat.init()
    }
}
