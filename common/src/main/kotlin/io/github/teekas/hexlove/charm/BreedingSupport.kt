package io.github.teekas.hexlove.charm

import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.registry.HexloveTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.entity.monster.ZombieVillager
import net.minecraft.world.entity.player.Player

/** Requirement 11.8 / 25.2: classify by features, never by a species list. */
enum class BreedCategory { FOOD_BREEDABLE, ZOMBIE, ZOMBIE_VILLAGER, OTHER }

object BreedingSupport {
    fun categoryOf(entity: Entity): BreedCategory = when {
        entity is ZombieVillager -> BreedCategory.ZOMBIE_VILLAGER
        entity is Zombie -> BreedCategory.ZOMBIE
        entity is Animal && !entity.type.`is`(HexloveTags.NOT_FOOD_BREEDABLE) -> BreedCategory.FOOD_BREEDABLE
        else -> BreedCategory.OTHER
    }

    /** Requirements 14.2 - 14.6: mating readiness of one entity, from its own features only. */
    fun facts(entity: Entity): TargetFacts {
        val living = entity as? LivingEntity
        val animal = entity as? Animal
        return TargetFacts(
            isPlayer = entity is Player,
            maxHealth = living?.maxHealth?.toDouble() ?: 0.0,
            isInvulnerable = entity.isInvulnerable,
            charmImmuneTag = entity.type.`is`(HexloveTags.CHARM_IMMUNE),
            breedCategory = categoryOf(entity),
            speciesId = entity.type.descriptionId,
            isBaby = living?.isBaby ?: false,
            hasBreedCooldown = (animal?.age ?: 0) > 0,
            inLove = animal?.isInLove ?: false,
            heartbroken = BondStore.isHeartbroken(entity),
        )
    }

    /** Requirement 14: ready to mate right now? Pure, from a snapshot. */
    fun isReadyToMate(facts: TargetFacts): Boolean =
        facts.breedCategory == BreedCategory.FOOD_BREEDABLE &&
            !facts.isBaby &&
            !facts.hasBreedCooldown &&
            !facts.heartbroken
}

/** Snapshot of the features every charm decision needs. No world access. */
data class TargetFacts(
    val isPlayer: Boolean,
    val maxHealth: Double,
    val isInvulnerable: Boolean,
    val charmImmuneTag: Boolean,
    val breedCategory: BreedCategory,
    val speciesId: String,
    val isBaby: Boolean,
    val hasBreedCooldown: Boolean,
    val inLove: Boolean,
    val heartbroken: Boolean,
)
