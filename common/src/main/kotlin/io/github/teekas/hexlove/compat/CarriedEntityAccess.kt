package io.github.teekas.hexlove.compat

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Optional bridge for mods which temporarily keep an entity inside a player's private data instead
 * of leaving it in a level. The common module deliberately knows nothing about a particular carrier
 * mod; platform integrations install [carrierCheck] only when their dependency is present.
 */
object CarriedEntityAccess {
    private var carrierCheck: ((ServerPlayer, UUID) -> Boolean)? = null

    /** Registers the platform-specific test for an entity held by [ServerPlayer]. */
    fun install(check: (ServerPlayer, UUID) -> Boolean) {
        carrierCheck = check
    }

    fun isCarriedBy(player: ServerPlayer, entity: UUID): Boolean =
        carrierCheck?.invoke(player, entity) == true

    /** Finds the player carrying [entity], if the installed integration can see one. */
    fun carrierOf(server: MinecraftServer, entity: UUID): ServerPlayer? {
        val check = carrierCheck ?: return null
        return server.playerList.players.firstOrNull { check(it, entity) }
    }
}
