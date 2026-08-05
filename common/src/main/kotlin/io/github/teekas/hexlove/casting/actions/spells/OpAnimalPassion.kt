package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationSpellAction
import io.github.teekas.hexlove.casting.HexloveMishaps
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.charm.BreedCategory
import io.github.teekas.hexlove.charm.BreedingSupport
import io.github.teekas.hexlove.config.HexloveServerConfig
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.Animal

/** What a single cast of Animal Passion does. Exactly one branch, always. */
enum class PassionPlan { SEEK_MATE, REDUCE_COOLDOWN, REFUSE }

object AnimalPassionPlanner {
    /**
     * Requirements 15.2 - 15.6, revised: shorten the cooldown, or start looking for a partner.
     *
     * A calf is refused outright. The first draft grew it up instead, which read as this spell
     * hurrying a child towards being breedable — not a thing this mod should do at any price.
     */
    fun plan(category: BreedCategory, heartbroken: Boolean, isBaby: Boolean, age: Int): PassionPlan = when {
        heartbroken || category != BreedCategory.FOOD_BREEDABLE -> PassionPlan.REFUSE
        isBaby || age < 0 -> PassionPlan.REFUSE
        age > 0 -> PassionPlan.REDUCE_COOLDOWN
        else -> PassionPlan.SEEK_MATE
    }

    /** Never crosses zero into nonsense. */
    fun reducedCooldown(age: Int, reduction: Int): Int = (age - reduction).coerceAtLeast(0)
}

/**
 * Requirement 15: the price is the same whichever branch runs, so the cast never leaks the animal's
 * private state to whoever is paying.
 */
object OpAnimalPassion : InfatuationSpellAction {
    override val argc = 1

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result =
        ActionGuard.guard("animal_passion") {
            val target = args.getEntity(0, argc)
            env.assertEntityInRange(target)

            val facts = BreedingSupport.facts(target)
            val plan = AnimalPassionPlanner.plan(facts.breedCategory, facts.heartbroken, facts.isBaby, (target as? Animal)?.age ?: 0)
            if (plan == PassionPlan.REFUSE) throw HexloveMishaps.immune(target)

            SpellAction.Result(
                Spell(target, plan),
                MediaCosts.animalPassion(),
                listOf(ParticleSpray.cloud(target.position().add(0.0, target.bbHeight / 2.0, 0.0), 1.0)),
            )
        }

    private data class Spell(val target: Entity, val plan: PassionPlan) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = ActionGuard.guardCast("animal_passion") {
            val animal = target as? Animal ?: return@guardCast
            if (BondStore.isHeartbroken(animal)) return@guardCast
            val cfg = HexloveServerConfig.config.breeding

            when (plan) {
                PassionPlan.SEEK_MATE -> animal.setInLoveTime(600)
                PassionPlan.REDUCE_COOLDOWN ->
                    animal.age = AnimalPassionPlanner.reducedCooldown(animal.age, cfg.passionCooldownReductionTicks)
                PassionPlan.REFUSE -> return@guardCast
            }

            env.world.sendParticles(
                ParticleTypes.HEART,
                animal.x, animal.y + animal.bbHeight * 0.75, animal.z,
                4, 0.3, 0.3, 0.3, 0.02,
            )
        }
    }
}
