package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.getPositiveDouble
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.bond.Durations
import io.github.teekas.hexlove.bond.SirenState
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationSpellAction
import io.github.teekas.hexlove.casting.HexloveMishaps
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.fx.BondFx
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity

/**
 * Requirement 20: a lure, not a bond. It moves the creature towards the caller and leaves target
 * selection and every existing bond exactly as they were.
 */
object OpSirensSong : InfatuationSpellAction {
    override val argc = 3

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result =
        ActionGuard.guard("sirens_song") {
            val controlled = args.getEntity(0, argc)
            val caller = args.getEntity(1, argc)
            val seconds = args.getPositiveDouble(2, argc)
            env.assertEntityInRange(controlled)
            env.assertEntityInRange(caller)

            if (!HexloveServerConfig.config.toggles.allowSirensSong) {
                throw HexloveMishaps.disallowed("sirens_song")
            }
            if (BondStore.isHeartbroken(controlled)) throw HexloveMishaps.immune(controlled)

            SpellAction.Result(
                Spell(controlled, caller, seconds),
                MediaCosts.sirensSong(seconds),
                listOf(ParticleSpray.cloud(controlled.position().add(0.0, controlled.bbHeight / 2.0, 0.0), 1.0)),
            )
        }

    private data class Spell(val controlled: Entity, val caller: Entity, val seconds: Double) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = ActionGuard.guardCast("sirens_song") {
            val expiresAt = Durations.expiryFromSeconds(
                seconds,
                env.world.gameTime,
                HexloveServerConfig.config.siren.maxDurationTicks,
            )
            BondStore.setSiren(controlled, SirenState(caller.uuid, expiresAt))

            env.world.sendParticles(
                ParticleTypes.NOTE,
                controlled.x, controlled.y + controlled.bbHeight * 0.9, controlled.z,
                6, 0.4, 0.2, 0.4, 0.0,
            )
            // the song travels from the caller to the called, and repeats itself every few seconds
            // for as long as it lasts — see BondTicker
            BondFx.sirenCall(env.world, caller, controlled)
        }
    }
}
