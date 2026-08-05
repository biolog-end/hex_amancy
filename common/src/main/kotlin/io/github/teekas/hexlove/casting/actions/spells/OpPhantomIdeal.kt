package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getPositiveDouble
import at.petrak.hexcasting.api.casting.getVec3
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.bond.Durations
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationSpellAction
import io.github.teekas.hexlove.casting.HexloveMishaps
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.entity.PhantomIdeal
import io.github.teekas.hexlove.fx.BondFx
import io.github.teekas.hexlove.registry.HexloveEntities
import net.minecraft.world.phys.Vec3

/** Summons a motionless rose-pink image that draws unbound creatures to itself for a short while. */
object OpPhantomIdeal : InfatuationSpellAction {
    override val argc = 2

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result =
        ActionGuard.guard("phantom_ideal") {
            val position = args.getVec3(0, argc)
            val seconds = args.getPositiveDouble(1, argc)
            env.assertVecInRange(position)
            if (!HexloveServerConfig.config.toggles.allowPhantomIdeal) {
                throw HexloveMishaps.disallowed("phantom_ideal")
            }
            val caster = env.castingEntity ?: throw HexloveMishaps.disallowed("phantom_ideal")
            SpellAction.Result(
                Spell(caster, position, seconds),
                MediaCosts.phantomIdeal(seconds),
                listOf(
                    ParticleSpray.cloud(caster.position().add(0.0, caster.bbHeight * 0.65, 0.0), 0.8),
                    ParticleSpray.burst(position.add(0.0, 0.9, 0.0), 1.2),
                ),
            )
        }

    private data class Spell(
        val caster: net.minecraft.world.entity.LivingEntity,
        val position: Vec3,
        val seconds: Double,
    ) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = ActionGuard.guardCast("phantom_ideal") {
            val phantom = try {
                HexloveEntities.PHANTOM_IDEAL.value.create(env.world) as? PhantomIdeal
            } catch (t: Throwable) {
                Hexlove.LOGGER.error("Failed to create a Phantom Ideal", t)
                null
            } ?: return@guardCast
            val expiresAt = Durations.expiryFromSeconds(
                seconds,
                env.world.gameTime,
                HexloveServerConfig.config.phantomIdeal.maxDurationTicks,
            )
            phantom.manifest(caster, position, expiresAt)
            env.world.addFreshEntity(phantom)
            phantom.attractNearby()
            BondFx.phantomManifestation(env.world, caster, phantom)
        }
    }
}
