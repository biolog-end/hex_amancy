package io.github.teekas.hexlove.nature

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent
import io.github.teekas.hexlove.Hexlove
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * Blocks Hex Casting from extracting media or reaching any vector target when the caster is
 * humiliated. Registered via the official [CastingEnvironment.addCreateEventListener] API —
 * no mixin into Hex Casting internals.
 *
 * Failure isolation is copied exactly from [io.github.teekas.hexlove.marriage.MarriageAmbitExtension]:
 * a [State] enum, [@Volatile], [disable] that logs once and never re-enables, everything wrapped
 * in try/catch. Any exception permanently disables this component; Hex Casting keeps working.
 *
 * Verified signatures from hexcasting-common-1.20.1-0.11.3.jar:
 *   CastingEnvironmentComponent.ExtractMedia.Pre extends ExtractMedia:
 *     long onExtractMedia(long cost, boolean simulate)
 *   CastingEnvironmentComponent.IsVecInRange:
 *     boolean onIsVecInRange(Vec3 vec, boolean current)
 */
object HumiliationCastBlock {
    private enum class State { ENABLED, DISABLED_ERROR }

    @Volatile
    private var state = State.ENABLED

    fun init() {
        try {
            CastingEnvironment.addCreateEventListener { environment ->
                environment.addExtension(Component(environment))
            }
        } catch (t: Throwable) {
            disable(t)
        }
    }

    private fun disable(error: Throwable) {
        if (state == State.DISABLED_ERROR) return
        state = State.DISABLED_ERROR
        Hexlove.LOGGER.warn(
            "Disabling humiliation cast block; other humiliation effects remain active.", error
        )
    }

    private fun isHumiliatedCaster(environment: CastingEnvironment): Boolean {
        if (state != State.ENABLED) return false
        return try {
            val player = environment.castingEntity as? ServerPlayer ?: return false
            ChimeraNatures.isHumiliated(player)
        } catch (t: Throwable) {
            disable(t)
            false
        }
    }

    private class Component(private val environment: CastingEnvironment) :
        CastingEnvironmentComponent.ExtractMedia.Pre,
        CastingEnvironmentComponent.IsVecInRange {

        // Each component interface needs its own Key. We use two separate private objects.
        override fun getKey(): CastingEnvironmentComponent.Key<*> = COMBINED_KEY

        /**
         * Returns Long.MAX_VALUE (no media available) when the caster is humiliated.
         * Any priced spell therefore fails as "not enough media".
         */
        override fun onExtractMedia(cost: Long, simulate: Boolean): Long {
            return try {
                if (isHumiliatedCaster(environment)) Long.MAX_VALUE else cost
            } catch (t: Throwable) {
                disable(t)
                cost
            }
        }

        /**
         * Returns false (vector out of range) for a humiliated caster, so any action
         * that needs a target location also fails.
         */
        override fun onIsVecInRange(vec: Vec3, current: Boolean): Boolean {
            return try {
                if (isHumiliatedCaster(environment)) false else current
            } catch (t: Throwable) {
                disable(t)
                current
            }
        }
    }

    /**
     * A component can only have one Key. Since our Component implements two interfaces we use a
     * single key that covers both. Hex Casting looks up components by Key equality, so as long as
     * only one instance is added per environment (which addCreateEventListener guarantees), this
     * is safe.
     */
    private object COMBINED_KEY : CastingEnvironmentComponent.Key<Component>
}
