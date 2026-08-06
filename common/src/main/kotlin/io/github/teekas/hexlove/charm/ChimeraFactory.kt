package io.github.teekas.hexlove.charm

import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.block.AmethystHeartManager
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.entity.Chimera
import io.github.teekas.hexlove.registry.HexloveEntities
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.animal.Animal

/**
 * Requirement 12: two animals of different species. Rather than a whole new entity type, the child
 * takes one parent's body and the *average* of both parents' vital statistics — health, speed, reach.
 * It is a chimera in the only sense that matters in play: it is neither of its parents.
 */
object ChimeraFactory {
    /** Pure: the blended value, never zero and never above the configured ceiling. */
    fun blend(a: Double, b: Double, cap: Double): Double =
        ((a + b) / 2.0).coerceIn(0.001, cap.coerceAtLeast(0.001))

    fun breed(level: ServerLevel, a: Animal, b: Animal, plan: BreedPlan): Int {
        val cfg = HexloveServerConfig.config.breeding
        BreedingExecutor.applyCooldown(a, plan)
        BreedingExecutor.applyCooldown(b, plan)

        if (level.random.nextDouble() > cfg.chimeraChance) {
            // the blood refused to mix
            level.sendParticles(
                ParticleTypes.SMOKE,
                a.x, a.y + a.bbHeight * 0.75, a.z,
                8, 0.3, 0.3, 0.3, 0.01,
            )
            return 0
        }

        // its own creature now, rather than one parent's body wearing a name tag
        val child = try {
            HexloveEntities.CHIMERA.value.create(level)
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("Failed to create a chimera from {} and {}", a.type.description.string, b.type.description.string, t)
            null
        } as? Chimera ?: return 0

        child.setBaby(true)
        child.moveTo(a.x, a.y, a.z, a.yRot, 0f)

        child.initializeFromParents(a, b, cfg.chimeraMaxSize, cfg.chimeraMaxHealth,
            sizeNoise = cfg.chimeraSizeNoise, healthNoise = cfg.chimeraHealthNoise,
            rng = level.random)
        average(child, Attributes.MOVEMENT_SPEED, a, b, 2.0)
        average(child, Attributes.ATTACK_DAMAGE, a, b, 64.0)
        child.health = child.maxHealth

        level.addFreshEntityWithPassengers(child)
        HexloveAdvancements.grantNearby(level, child.position(), HexloveAdvancements.SPAWN_CHIMERA)
        AmethystHeartManager.onAnimalBred(level, a, b)
        BreedingExecutor.hearts(level, a)
        BreedingExecutor.hearts(level, b)
        return 1
    }

    private fun average(child: Mob, attribute: Attribute, a: Animal, b: Animal, cap: Double) {
        val instance = child.getAttribute(attribute) ?: return
        val left = a.getAttribute(attribute)?.baseValue ?: return
        val right = b.getAttribute(attribute)?.baseValue ?: return
        instance.baseValue = blend(left, right, cap)
    }
}
