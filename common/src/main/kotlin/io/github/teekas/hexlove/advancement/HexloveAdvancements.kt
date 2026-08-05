package io.github.teekas.hexlove.advancement

import io.github.teekas.hexlove.Hexlove
import net.minecraft.advancements.Advancement
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/** Awards the data-driven advancements whose conditions are fulfilled by Hex: Amancy mechanics. */
object HexloveAdvancements {
    private const val ROOT = "root"

    const val ROMANTIC_SETTING = "romantic_setting"
    const val ENERGY_OF_PASSION = "energy_of_passion"
    const val SPAWN_CHIMERA = "spawn_chimera"
    const val BIZARRE_GOURMAND = "bizarre_gourmand"
    const val DOUBLE_MUTATION = "double_mutation"
    const val CUPIDS_ARROW = "cupids_arrow"
    const val LOVE_IS_BLIND = "love_is_blind"
    const val HEARTBREAKER = "heartbreaker"
    const val FLAWLESS_ILLUSION = "flawless_illusion"
    const val LOVE_IS_IN_THE_AIR = "love_is_in_the_air"
    const val SOULBOUND = "soulbound"
    const val TILL_SERVER_DO_US_PART = "till_server_do_us_part"
    const val IN_SICKNESS_AND_IN_HEALTH = "in_sickness_and_in_health"
    const val SUCCUBUS = "succubus"
    const val HEART_OF_THE_WORLD = "heart_of_the_world"
    const val SECRETS_OF_THE_ANCIENTS = "secrets_of_the_ancients"

    fun grant(player: ServerPlayer, path: String) {
        val advancement = player.server.advancements.getAdvancement(Hexlove.id(path)) ?: return
        grantAdvancement(player, advancement)
    }

    /** Awards the complete Hex: Amancy advancement tree, including hidden challenges. */
    @JvmStatic
    fun grantAll(player: ServerPlayer) {
        val root = player.server.advancements.getAdvancement(Hexlove.id(ROOT)) ?: return
        grantTree(player, root)
    }

    private fun grantTree(player: ServerPlayer, advancement: Advancement) {
        grantAdvancement(player, advancement)
        advancement.children.forEach { child -> grantTree(player, child) }
    }

    private fun grantAdvancement(player: ServerPlayer, advancement: Advancement) {
        val progress = player.advancements.getOrStartProgress(advancement)
        progress.remainingCriteria.toList().forEach { criterion ->
            player.advancements.award(advancement, criterion)
        }
    }

    fun grantNearby(level: ServerLevel, centre: Vec3, path: String, radius: Double = 32.0) {
        val radiusSqr = radius * radius
        level.players().asSequence()
            .filter { it.isAlive && it.distanceToSqr(centre) <= radiusSqr }
            .forEach { grant(it, path) }
    }
}
