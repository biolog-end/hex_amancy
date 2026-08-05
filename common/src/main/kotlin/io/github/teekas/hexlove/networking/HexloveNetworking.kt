package io.github.teekas.hexlove.networking

import dev.architectury.networking.NetworkChannel
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.networking.msg.HexloveMessageCompanion

object HexloveNetworking {
    val CHANNEL: NetworkChannel = NetworkChannel.create(Hexlove.id("networking_channel"))

    fun init() {
        for (subclass in HexloveMessageCompanion::class.sealedSubclasses) {
            subclass.objectInstance?.register(CHANNEL)
        }
    }
}
