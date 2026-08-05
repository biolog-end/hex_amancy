@file:JvmName("HexloveUtil")

package io.github.teekas.hexlove

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import java.util.UUID

/** Loaded entity with this uuid in the same dimension, or null. */
fun Entity.sameLevelEntity(uuid: UUID): Entity? = (level() as? ServerLevel)?.getEntity(uuid)

fun Entity.sameLevelLiving(uuid: UUID): LivingEntity? = sameLevelEntity(uuid) as? LivingEntity

/** Loaded entity with this uuid anywhere on the server, or null. */
fun MinecraftServer.findEntity(uuid: UUID): Entity? {
    for (level in allLevels) {
        level.getEntity(uuid)?.let { return it }
    }
    return null
}

val Entity.mcServer: MinecraftServer?
    get() = level().server
