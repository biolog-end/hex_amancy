package io.github.teekas.hexlove.fabric

import io.github.teekas.hexlove.Hexlove
import net.fabricmc.api.DedicatedServerModInitializer

object FabricHexloveServer : DedicatedServerModInitializer {
    override fun onInitializeServer() {
        Hexlove.initServer()
    }
}
