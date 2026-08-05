package io.github.teekas.hexlove.networking

import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.compat.CarriedEntityAccess
import io.github.teekas.hexlove.networking.msg.MsgSyncBondTargetS2C
import io.github.teekas.hexlove.networking.msg.MsgSyncBondsS2C
import io.github.teekas.hexlove.world.HexloveWorldData
import io.github.teekas.hexlove.marriage.MarriageManager
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.Optional
import java.util.UUID

/** Bond_Sync: pushes a player's own states to their client (Requirements 5.1, 5.2, 5.3, 5.5). */
object BondSync {
    fun sync(player: ServerPlayer) {
        val love = BondStore.love(player)
        val jealousy = BondStore.jealousy(player)
        val heartbreak = BondStore.heartbreak(player)
        val siren = BondStore.siren(player)
        val married = player.level().server?.let { MarriageManager.isActive(it, player.uuid) } == true

        MsgSyncBondsS2C(
            loveTarget = love?.target,
            loveExpiresAt = love?.expiresAt ?: 0L,
            lovePermanent = love?.permanent ?: false,
            jealousyTarget = jealousy?.target,
            jealousyExpiresAt = jealousy?.expiresAt ?: 0L,
            heartbreakExpiresAt = heartbreak?.expiresAt,
            married = married,
            sirenCaller = siren?.caller,
        ).sendToPlayer(player)

        syncTarget(player)
    }

    /**
     * The overlays follow the beloved continuously, so this is pushed on a short interval by the bond
     * ticker as well as on every change (Requirement 5.3).
     */
    fun syncTarget(player: ServerPlayer, jealousyDistance: Double? = null) {
        MsgSyncBondTargetS2C(
            position = positionOf(player, BondStore.love(player)?.target),
            jealousyDistance = jealousyDistance ?: -1.0,
            sirenPosition = positionOf(player, BondStore.siren(player)?.caller),
        ).sendToPlayer(player)
    }

    private fun positionOf(player: ServerPlayer, target: UUID?): Optional<net.minecraft.world.phys.Vec3> {
        val targetId = target ?: return Optional.empty()
        val entity = (player.level() as? ServerLevel)?.getEntity(targetId)
        if (entity != null) return Optional.of(entity.position())
        // Feed the client the carrier's position so a held beloved reads as immediately present:
        // the love vignette reaches its normal maximum and the camera has nothing to pull towards.
        if (CarriedEntityAccess.isCarriedBy(player, targetId)) return Optional.of(player.position())
        return Optional.empty()
    }
}
