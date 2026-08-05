package io.github.teekas.hexlove.marriage

import at.petrak.hexcasting.api.HexAPI
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent
import at.petrak.hexcasting.common.lib.HexAttributes
import io.github.teekas.hexlove.Hexlove
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * Adds a spouse's ambit to a player's casts. Hex Casting exposes [CastingEnvironmentComponent]
 * specifically for range extensions, so this covers every Hex action without a mixin into its
 * private casting implementation.
 */
object MarriageAmbitExtension {
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

    private fun allows(environment: CastingEnvironment, position: Vec3): Boolean {
        if (state != State.ENABLED) return false
        return try {
            val caster = environment.castingEntity as? ServerPlayer ?: return false
            val server = environment.world.server
            val spouseId = MarriageManager.spouseOf(server, caster.uuid) ?: return false
            val spouse = server.playerList.getPlayer(spouseId) ?: return false
            if (!spouse.isAlive || spouse.level() !== environment.world) return false
            isInPlayerAmbit(spouse, position)
        } catch (t: Throwable) {
            disable(t)
            false
        }
    }

    /** Mirrors Hex Casting's [at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv] check. */
    private fun isInPlayerAmbit(player: ServerPlayer, position: Vec3): Boolean {
        val ordinaryRadius = player.getAttributeValue(HexAttributes.AMBIT_RADIUS)
        if (position.distanceToSqr(player.position()) <= ordinaryRadius * ordinaryRadius + EPSILON) return true

        val sentinel = HexAPI.instance().getSentinel(player) ?: return false
        if (!sentinel.extendsRange() || player.level().dimension() != sentinel.dimension()) return false
        val sentinelRadius = player.getAttributeValue(HexAttributes.SENTINEL_RADIUS)
        return position.distanceToSqr(sentinel.position()) <= sentinelRadius * sentinelRadius + EPSILON
    }

    private fun disable(error: Throwable) {
        if (state == State.DISABLED_ERROR) return
        state = State.DISABLED_ERROR
        Hexlove.LOGGER.warn("Disabling marriage ambit extension; other marriage effects remain active.", error)
    }

    private class Component(private val environment: CastingEnvironment) : CastingEnvironmentComponent.IsVecInRange {
        override fun getKey(): CastingEnvironmentComponent.Key<*> = KEY

        override fun onIsVecInRange(position: Vec3, current: Boolean): Boolean = current || allows(environment, position)
    }

    private object KEY : CastingEnvironmentComponent.Key<Component>

    private const val EPSILON = 0.00000000001
}
