package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.getPositiveDouble
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.bond.Durations
import io.github.teekas.hexlove.bond.HeartbreakState
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationSpellAction
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.config.HexloveServerConfig
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.server.level.ServerPlayer

/**
 * Requirement 18: the antidote and the wound in one pattern. Takes a plain duration in seconds, not
 * a strength of feeling: breaking a heart needs no talent.
 */
object OpHeartbreak : InfatuationSpellAction {
    override val argc = 2

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result =
        ActionGuard.guard("heartbreak") {
            val target = args.getEntity(0, argc)
            val seconds = args.getPositiveDouble(1, argc)
            // the caster may aim this at themselves: it is the only way out of a charm
            env.assertEntityInRange(target)

            SpellAction.Result(
                Spell(target, seconds),
                MediaCosts.heartbreak(),
                listOf(ParticleSpray.burst(target.position().add(0.0, target.bbHeight / 2.0, 0.0), 1.0)),
            )
        }

    private data class Spell(val target: Entity, val seconds: Double) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = ActionGuard.guardCast("heartbreak") {
            val severedBond = BondStore.love(target) != null ||
                BondStore.jealousy(target) != null ||
                BondStore.siren(target) != null ||
                BondStore.courtship(target) != null
            val expiresAt = Durations.expiryFromSeconds(
                seconds,
                env.world.gameTime,
                HexloveServerConfig.config.heartbreak.maxDurationTicks,
            )
            // one operation: love and jealousy go away with it (Requirements 18.3, 4.7)
            BondStore.setHeartbreak(target, HeartbreakState(expiresAt))
            if (severedBond) {
                (env.castingEntity as? ServerPlayer)?.let {
                    HexloveAdvancements.grant(it, HexloveAdvancements.HEARTBREAKER)
                }
            }

            env.world.sendParticles(
                ParticleTypes.SMOKE,
                target.x, target.y + target.bbHeight * 0.75, target.z,
                8, 0.3, 0.3, 0.3, 0.01,
            )
        }
    }
}
