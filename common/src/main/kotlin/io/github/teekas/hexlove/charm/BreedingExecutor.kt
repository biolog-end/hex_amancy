package io.github.teekas.hexlove.charm

import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.block.AmethystHeartManager
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.entity.monster.ZombieVillager

/**
 * Turns a [BreedPlan] into actual children. Uses vanilla's own offspring factory, so species with
 * egg-laying or any other intermediate step behave exactly as they do when bred with wheat —
 * only repeated as many times as the plan says (Requirement 11.9).
 */
object BreedingExecutor {
    fun breedAnimals(level: ServerLevel, a: Animal, b: Animal, plan: BreedPlan): Int {
        var spawned = 0
        repeat(plan.offspring) {
            val child = try {
                a.getBreedOffspring(level, b as AgeableMob)
            } catch (t: Throwable) {
                Hexlove.LOGGER.error("Failed to create offspring of {}", a.type.description.string, t)
                null
            } ?: return@repeat
            child.setBaby(true)
            child.moveTo(a.x, a.y, a.z, a.yRot, 0f)
            level.addFreshEntityWithPassengers(child)
            AmethystHeartManager.onAnimalBred(level, a, b)
            spawned++
        }
        applyCooldown(a, plan)
        applyCooldown(b, plan)
        hearts(level, a)
        hearts(level, b)
        return spawned
    }

    /** Requirement 13: an undead child is indistinguishable from one that wandered in on its own. */
    fun breedUndead(level: ServerLevel, a: Entity, b: Entity, plan: BreedPlan): Int {
        val type: EntityType<*> = when {
            a is ZombieVillager && b is ZombieVillager -> EntityType.ZOMBIE_VILLAGER
            a is Zombie && b is Zombie -> EntityType.ZOMBIE
            else -> return 0
        }
        var spawned = 0
        repeat(plan.offspring) {
            val child = type.create(level) as? Mob ?: return@repeat
            child.moveTo(a.x, a.y, a.z, a.yRot, 0f)
            (child as? Zombie)?.isBaby = true
            child.finalizeSpawn(
                level,
                level.getCurrentDifficultyAt(BlockPos.containing(a.position())),
                MobSpawnType.BREEDING,
                null,
                null,
            )
            (child as? Zombie)?.isBaby = true
            level.addFreshEntityWithPassengers(child)
            spawned++
        }
        hearts(level, a)
        hearts(level, b)
        return spawned
    }

    /** Requirement 11.10: both parents pay the cooldown, whatever came of the attempt. */
    fun applyCooldown(entity: Entity, plan: BreedPlan) {
        val animal = entity as? Animal ?: return
        animal.resetLove()
        if (animal.age < plan.cooldownTicks) animal.age = plan.cooldownTicks
    }

    fun hearts(level: ServerLevel, entity: Entity) {
        level.sendParticles(
            ParticleTypes.HEART,
            entity.x, entity.y + entity.bbHeight * 0.75, entity.z,
            6, 0.4, 0.3, 0.4, 0.02,
        )
    }
}
