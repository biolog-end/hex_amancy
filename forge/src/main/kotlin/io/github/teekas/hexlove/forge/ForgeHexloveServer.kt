package io.github.teekas.hexlove.forge

import io.github.teekas.hexlove.Hexlove
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent

object ForgeHexloveServer {
    @Suppress("UNUSED_PARAMETER")
    fun init(event: FMLDedicatedServerSetupEvent) {
        Hexlove.initServer()
    }
}
