package io.github.teekas.hexlove.networking.msg

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.phys.Vec3
import java.util.Optional

/**
 * Requirements 5.3, 5.5: where the beloved is, if reachable at all — plus the two other distances the
 * client needs for its overlays.
 *
 * An unreachable target is [Optional.empty], never a zero vector — the client shows the minimum
 * overlay and stops nudging the camera (Requirement 9.11).
 */
data class MsgSyncBondTargetS2C(
    val position: Optional<Vec3>,
    /** Requirement 16.7: how close the nearest stranger is to the guarded one. Negative if none. */
    val jealousyDistance: Double = -1.0,
    /** Requirement 20, revised: where the song is coming from, so the view can be held on it. */
    val sirenPosition: Optional<Vec3> = Optional.empty(),
) : HexloveMessageS2C {
    val available: Boolean get() = position.isPresent

    companion object : HexloveMessageCompanion<MsgSyncBondTargetS2C> {
        override val type = MsgSyncBondTargetS2C::class.java

        override fun decode(buf: FriendlyByteBuf) = MsgSyncBondTargetS2C(
            position = buf.readOptionalVec3(),
            jealousyDistance = buf.readDouble(),
            sirenPosition = buf.readOptionalVec3(),
        )

        override fun MsgSyncBondTargetS2C.encode(buf: FriendlyByteBuf) {
            buf.writeOptionalVec3(position)
            buf.writeDouble(jealousyDistance)
            buf.writeOptionalVec3(sirenPosition)
        }
    }
}

private fun FriendlyByteBuf.writeOptionalVec3(value: Optional<Vec3>) {
    writeBoolean(value.isPresent)
    value.ifPresent {
        writeDouble(it.x)
        writeDouble(it.y)
        writeDouble(it.z)
    }
}

private fun FriendlyByteBuf.readOptionalVec3(): Optional<Vec3> =
    if (readBoolean()) Optional.of(Vec3(readDouble(), readDouble(), readDouble())) else Optional.empty()
