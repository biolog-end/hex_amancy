package io.github.teekas.hexlove.networking.msg

import net.minecraft.network.FriendlyByteBuf

/** A sensory cue only: clients draw it locally for two seconds; it carries no control effect. */
data class MsgPheromoneTouchS2C(val marker: Unit = Unit) : HexloveMessageS2C {
    companion object : HexloveMessageCompanion<MsgPheromoneTouchS2C> {
        override val type = MsgPheromoneTouchS2C::class.java

        override fun decode(buf: FriendlyByteBuf): MsgPheromoneTouchS2C = MsgPheromoneTouchS2C()

        override fun MsgPheromoneTouchS2C.encode(buf: FriendlyByteBuf) = Unit
    }
}
