package io.github.teekas.hexlove.networking.msg

import net.minecraft.network.FriendlyByteBuf
import java.util.UUID

/** Requirements 5.1, 5.2: full snapshot of one player's states, sent on every change and on join. */
data class MsgSyncBondsS2C(
    val loveTarget: UUID?,
    val loveExpiresAt: Long,
    val lovePermanent: Boolean,
    val jealousyTarget: UUID?,
    val jealousyExpiresAt: Long,
    val heartbreakExpiresAt: Long?,
    val married: Boolean,
    val sirenCaller: UUID? = null,
) : HexloveMessageS2C {
    companion object : HexloveMessageCompanion<MsgSyncBondsS2C> {
        override val type = MsgSyncBondsS2C::class.java

        override fun decode(buf: FriendlyByteBuf) = MsgSyncBondsS2C(
            loveTarget = buf.readNullableUUID(),
            loveExpiresAt = buf.readLong(),
            lovePermanent = buf.readBoolean(),
            jealousyTarget = buf.readNullableUUID(),
            jealousyExpiresAt = buf.readLong(),
            heartbreakExpiresAt = if (buf.readBoolean()) buf.readLong() else null,
            married = buf.readBoolean(),
            sirenCaller = buf.readNullableUUID(),
        )

        override fun MsgSyncBondsS2C.encode(buf: FriendlyByteBuf) {
            buf.writeNullableUUID(loveTarget)
            buf.writeLong(loveExpiresAt)
            buf.writeBoolean(lovePermanent)
            buf.writeNullableUUID(jealousyTarget)
            buf.writeLong(jealousyExpiresAt)
            buf.writeBoolean(heartbreakExpiresAt != null)
            heartbreakExpiresAt?.let(buf::writeLong)
            buf.writeBoolean(married)
            buf.writeNullableUUID(sirenCaller)
        }
    }
}

internal fun FriendlyByteBuf.writeNullableUUID(uuid: UUID?) {
    writeBoolean(uuid != null)
    if (uuid != null) writeUUID(uuid)
}

internal fun FriendlyByteBuf.readNullableUUID(): UUID? = if (readBoolean()) readUUID() else null
