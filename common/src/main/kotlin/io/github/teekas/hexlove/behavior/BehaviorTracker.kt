package io.github.teekas.hexlove.behavior

import net.minecraft.world.entity.Entity
import net.minecraft.server.level.ServerLevel
import java.util.Collections
import java.util.WeakHashMap

/**
 * Transient set of entities that currently carry any state of this mod, so the ticker never has to
 * walk every loaded entity. Filled when a bond is created and on entity load; cleared on unload and death.
 */
object BehaviorTracker {
    private val active: MutableSet<Entity> = Collections.newSetFromMap(WeakHashMap())

    fun track(entity: Entity) {
        active.add(entity)
    }

    fun untrack(entity: Entity) {
        active.remove(entity)
    }

    fun isTracked(entity: Entity): Boolean = active.contains(entity)

    /**
     * The ticker runs once per loaded level. Keep its temporary snapshot limited to that level
     * instead of copying every bonded entity on the server for every dimension.
     */
    fun snapshot(level: ServerLevel): List<Entity> {
        val snapshot = ArrayList<Entity>()
        for (entity in active) {
            if (entity.level() === level) snapshot.add(entity)
        }
        return snapshot
    }

    fun clear() = active.clear()
}
