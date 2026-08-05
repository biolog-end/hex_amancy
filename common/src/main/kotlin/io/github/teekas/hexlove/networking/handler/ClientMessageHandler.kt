package io.github.teekas.hexlove.networking.handler

import dev.architectury.networking.NetworkManager.PacketContext
import io.github.teekas.hexlove.client.ClientBondState
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.networking.msg.*

fun HexloveMessageS2C.applyOnClient(ctx: PacketContext) = ctx.queue {
    when (this) {
        is MsgSyncConfigS2C -> {
            HexloveServerConfig.onSyncConfig(serverConfig)
        }

        is MsgSyncBondsS2C -> {
            ClientBondState.loveTarget = loveTarget
            ClientBondState.loveExpiresAt = loveExpiresAt
            ClientBondState.lovePermanent = lovePermanent
            ClientBondState.jealousyTarget = jealousyTarget
            ClientBondState.heartbroken = heartbreakExpiresAt != null
            ClientBondState.married = married
            ClientBondState.sirenCaller = sirenCaller
            if (loveTarget == null) ClientBondState.targetPosition = null
            if (jealousyTarget == null) ClientBondState.jealousyDistance = null
            if (sirenCaller == null) ClientBondState.sirenPosition = null
        }

        is MsgSyncBondTargetS2C -> {
            ClientBondState.targetPosition = position.orElse(null)
            ClientBondState.jealousyDistance = jealousyDistance.takeIf { it >= 0.0 }
            ClientBondState.sirenPosition = sirenPosition.orElse(null)
        }

        is MsgPheromoneTouchS2C -> {
            ClientBondState.pheromonePulseTicks = maxOf(ClientBondState.pheromonePulseTicks, 40)
        }

    }
}
