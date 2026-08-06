package io.github.teekas.hexlove.networking.msg

import dev.architectury.networking.NetworkChannel
import dev.architectury.networking.NetworkManager.PacketContext
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.networking.HexloveNetworking
import io.github.teekas.hexlove.networking.handler.applyOnClient
import io.github.teekas.hexlove.networking.handler.applyOnServer
import net.fabricmc.api.EnvType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import java.util.function.Supplier

sealed interface HexloveMessage

sealed interface HexloveMessageC2S : HexloveMessage {
    fun sendToServer() {
        HexloveNetworking.CHANNEL.sendToServer(this)
    }
}

sealed interface HexloveMessageS2C : HexloveMessage {
    fun sendToPlayer(player: ServerPlayer) {
        HexloveNetworking.CHANNEL.sendToPlayer(player, this)
    }

    fun sendToPlayers(players: Iterable<ServerPlayer>) {
        HexloveNetworking.CHANNEL.sendToPlayers(players, this)
    }
}

sealed interface HexloveMessageCompanion<T : HexloveMessage> {
    val type: Class<T>

    fun decode(buf: FriendlyByteBuf): T

    fun T.encode(buf: FriendlyByteBuf)

    fun apply(msg: T, supplier: Supplier<PacketContext>) {
        val ctx = supplier.get()
        when (ctx.env) {
            EnvType.SERVER, null -> {
                // gameProfile.name is the plain string; player.name.string built a fresh Component
                // walk for every single packet even with debug logging switched off. And the old
                // line logged the companion instead of the message, which was never useful.
                Hexlove.LOGGER.debug("Server received packet from {}: {}", ctx.player.gameProfile.name, msg)
                when (msg) {
                    is HexloveMessageC2S -> msg.applyOnServer(ctx)
                    else -> Hexlove.LOGGER.warn("Message not handled on server: {}", msg::class)
                }
            }
            EnvType.CLIENT -> {
                Hexlove.LOGGER.debug("Client received packet: {}", msg)
                when (msg) {
                    is HexloveMessageS2C -> msg.applyOnClient(ctx)
                    else -> Hexlove.LOGGER.warn("Message not handled on client: {}", msg::class)
                }
            }
        }
    }

    fun register(channel: NetworkChannel) {
        channel.register(type, { msg, buf -> msg.encode(buf) }, ::decode, ::apply)
    }
}
