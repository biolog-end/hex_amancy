package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.casting.InfatuationSpellAction
import io.github.teekas.hexlove.casting.HexloveMishaps
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.fx.BondFx
import io.github.teekas.hexlove.marriage.MarriageManager
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance

/**
 * Exchanges every active potion effect and the complete food state of two married players.
 *
 * Unlike shared ambit, this spell has dimension-aware targets: both player entities are already
 * loaded by the server, so their effect and food state can be exchanged without treating one
 * world's coordinates as coordinates in another. A remote exchange can only be initiated by one
 * of the spouses, and only while both are carrying their matching rings.
 */
object OpBreathExchange : InfatuationSpellAction {
    override val argc = 2

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result =
        ActionGuard.guard("breath_exchange") {
            val first = playerArg(args, 0)
            val second = playerArg(args, 1)
            val server = env.world.server

            if (!isMarriedPair(server, first, second)) {
                throw HexloveMishaps.breathExchange(first, second, "not_spouses")
            }
            if (!MarriageManager.carriesOwnRing(server, first) || !MarriageManager.carriesOwnRing(server, second)) {
                throw HexloveMishaps.breathExchange(first, second, "rings")
            }
            assertInRangeOrVow(env, first, second)

            val castOrigin = env.castingEntity?.position() ?: first.position()
            SpellAction.Result(
                Spell(first, second),
                MediaCosts.breathExchange(),
                listOf(ParticleSpray.cloud(castOrigin.add(0.0, 0.8, 0.0), 0.65)),
            )
        }

    private fun playerArg(args: List<Iota>, index: Int): ServerPlayer {
        val entity = args.getEntity(index, argc)
        return entity as? ServerPlayer
            ?: throw HexloveMishaps.incorrectIota(args[index], argc - 1 - index, "entity.player")
    }

    private fun isMarriedPair(server: MinecraftServer, first: ServerPlayer, second: ServerPlayer): Boolean {
        if (first.uuid == second.uuid) return false
        val record = MarriageManager.record(server, first.uuid) ?: return false
        return record.contains(first.uuid) && record.contains(second.uuid) && record.other(first.uuid) == second.uuid
    }

    /**
     * Same-world targets retain the ordinary Hex range check. A remote target is an intentional,
     * narrow exception: it is accepted only when the caster themself is one of this confirmed pair.
     */
    private fun assertInRangeOrVow(env: CastingEnvironment, first: ServerPlayer, second: ServerPlayer) {
        val remote = listOf(first, second).filter { it.level() !== env.world }
        if (remote.isNotEmpty()) {
            val caster = env.castingEntity as? ServerPlayer
            if (caster == null || (caster.uuid != first.uuid && caster.uuid != second.uuid)) {
                throw HexloveMishaps.outOfAmbit(remote.first())
            }
        }
        listOf(first, second).filter { it.level() === env.world }.forEach(env::assertEntityInRange)
    }

    private data class Spell(val first: ServerPlayer, val second: ServerPlayer) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = ActionGuard.guardCast("breath_exchange") {
            val server = env.world.server
            if (!first.isAlive || !second.isAlive || !isMarriedPair(server, first, second)) return@guardCast
            if (!MarriageManager.carriesOwnRing(server, first) || !MarriageManager.carriesOwnRing(server, second)) return@guardCast

            exchangeEffects(first, second)
            exchangeFood(first, second)
            BondFx.breathExchange(first, second)
            if (first.level() !== second.level()) {
                (env.castingEntity as? ServerPlayer)?.let {
                    HexloveAdvancements.grant(it, HexloveAdvancements.IN_SICKNESS_AND_IN_HEALTH)
                }
            }
        }
    }

    /** Copy instances before clearing either player, preserving duration, amplifier and hidden chains. */
    private fun exchangeEffects(first: ServerPlayer, second: ServerPlayer) {
        val firstEffects = first.activeEffects.map(::MobEffectInstance)
        val secondEffects = second.activeEffects.map(::MobEffectInstance)
        first.removeAllEffects()
        second.removeAllEffects()
        secondEffects.forEach(first::addEffect)
        firstEffects.forEach(second::addEffect)
    }

    /** Hunger means more than the visible drumsticks, so saturation and exhaustion travel with them. */
    private fun exchangeFood(first: ServerPlayer, second: ServerPlayer) {
        val firstFood = first.foodData
        val secondFood = second.foodData
        val firstState = FoodState(firstFood.foodLevel, firstFood.saturationLevel, firstFood.exhaustionLevel)
        val secondState = FoodState(secondFood.foodLevel, secondFood.saturationLevel, secondFood.exhaustionLevel)
        firstFood.setFoodLevel(secondState.food)
        firstFood.setSaturation(secondState.saturation)
        firstFood.setExhaustion(secondState.exhaustion)
        secondFood.setFoodLevel(firstState.food)
        secondFood.setSaturation(firstState.saturation)
        secondFood.setExhaustion(firstState.exhaustion)
    }

    private data class FoodState(val food: Int, val saturation: Float, val exhaustion: Float)
}
