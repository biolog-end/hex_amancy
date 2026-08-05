package io.github.teekas.hexlove.networking.handler

import dev.architectury.networking.NetworkManager.PacketContext
import io.github.teekas.hexlove.networking.msg.*

fun HexloveMessageC2S.applyOnServer(ctx: PacketContext) = ctx.queue {
    // NOTE: this is commented out because otherwise it fails to compile if there's nothing inside of the when expression
    /*
    when (this) {
        // add server-side message handlers here
    }
    */
}
