package io.github.teekas.hexlove.casting

import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.OperationResult
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughMedia
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds
import io.github.teekas.hexlove.registry.HexloveBlocks
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity

/** Applies one, non-stacking 20% reduction only to Hex: Amancy actions cast beside its candles. */
object InfatuationCost {
    private const val RADIUS = 8

    /**
     * One hex can hold dozens of this mod's actions, and every one of them used to sweep the whole
     * seventeen-block cube on its own. Nobody walks anywhere between two actions of the same cast,
     * so the answer is remembered for the caster's current tick and the sweep runs once instead.
     *
     * Yes, one slot. Casting is single-threaded and one caster is casting at a time; a second one
     * simply misses and pays for their own sweep. I stared at a fancier cache for an hour and it
     * bought nothing.
     */
    private class Probe(val caster: LivingEntity, val block: BlockPos, val tick: Long, val near: Boolean)

    @Volatile
    private var lastProbe: Probe? = null

    fun discount(env: CastingEnvironment, cost: Long): Long {
        if (cost <= 0L || !isNearCandle(env.castingEntity)) return cost
        return (cost * 4L) / 5L
    }

    private fun isNearCandle(caster: LivingEntity?): Boolean {
        caster ?: return false
        val level = caster.level()
        val centre = caster.blockPosition()
        val tick = level.gameTime

        val cached = lastProbe
        if (cached != null && cached.caster === caster && cached.tick == tick && cached.block == centre) {
            return cached.near
        }

        var near = false
        val min = centre.offset(-RADIUS, -RADIUS, -RADIUS)
        val max = centre.offset(RADIUS, RADIUS, RADIUS)
        for (pos in BlockPos.betweenClosed(min, max)) {
            if (pos.distSqr(centre) > RADIUS.toDouble() * RADIUS) continue
            if (level.getBlockState(pos).`is`(HexloveBlocks.CANDLES_OF_INFATUATION.value)) {
                near = true
                break
            }
        }
        lastProbe = Probe(caster, centre.immutable(), tick, near)
        return near
    }
}

/** The normal SpellAction contract with the candle's situational price substituted immediately before payment. */
interface InfatuationSpellAction : SpellAction {
    /** Optional values to leave on the stack after the spell has been accepted. */
    fun resultStack(result: SpellAction.Result): List<Iota> = emptyList()

    override fun operate(env: CastingEnvironment, image: CastingImage, continuation: SpellContinuation): OperationResult {
        val stack = image.stack.toMutableList()
        if (argc > stack.size) throw MishapNotEnoughArgs(argc, stack.size)
        val args = stack.takeLast(argc)
        repeat(argc) { stack.removeLast() }

        val userData = image.userData.copy()
        val result = executeWithUserdata(args, env, userData)
        stack.addAll(resultStack(result))
        val cost = InfatuationCost.discount(env, result.cost)
        val sideEffects = mutableListOf<OperatorSideEffect>()
        if (env.extractMedia(cost, true) > 0) throw MishapNotEnoughMedia(cost)
        if (cost < result.cost) {
            (env.castingEntity as? ServerPlayer)?.let {
                HexloveAdvancements.grant(it, HexloveAdvancements.ROMANTIC_SETTING)
            }
        }
        if (cost > 0L) sideEffects += OperatorSideEffect.ConsumeMedia(cost)
        sideEffects += OperatorSideEffect.AttemptSpell(result.effect, hasCastingSound(env), awardsCastingStat(env))
        result.particles.forEach { sideEffects += OperatorSideEffect.Particles(it) }
        return OperationResult(
            image.copy(stack = stack, opsConsumed = image.opsConsumed + result.opCount, userData = userData),
            sideEffects,
            continuation,
            if (hasCastingSound(env)) HexEvalSounds.SPELL else HexEvalSounds.MUTE,
        )
    }
}

/** The same adjustment for the inexpensive, read-only Hex: Amancy actions. */
interface InfatuationConstMediaAction : ConstMediaAction {
    override fun operate(env: CastingEnvironment, image: CastingImage, continuation: SpellContinuation): OperationResult {
        val stack = image.stack.toMutableList()
        if (argc > stack.size) throw MishapNotEnoughArgs(argc, stack.size)
        val args = stack.takeLast(argc)
        repeat(argc) { stack.removeLast() }
        val result = executeWithOpCount(args, env)
        stack.addAll(result.resultStack)

        val cost = InfatuationCost.discount(env, mediaCost)
        if (env.extractMedia(cost, true) > 0) throw MishapNotEnoughMedia(cost)
        if (cost < mediaCost) {
            (env.castingEntity as? ServerPlayer)?.let {
                HexloveAdvancements.grant(it, HexloveAdvancements.ROMANTIC_SETTING)
            }
        }
        return OperationResult(
            image.copy(stack = stack, opsConsumed = image.opsConsumed + result.opCount),
            mutableListOf(OperatorSideEffect.ConsumeMedia(cost)),
            continuation,
            HexEvalSounds.NORMAL_EXECUTE,
        )
    }
}
