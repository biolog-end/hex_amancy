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
import io.github.teekas.hexlove.bond.JealousyBond
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationSpellAction
import io.github.teekas.hexlove.casting.HexloveMishaps
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.fx.BondFx
import io.github.teekas.hexlove.marriage.MarriageManager
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.monster.Enemy

/**
 * Requirement 16: the jealous one guards the protected one from everybody else — but is still
 * forbidden to touch the object of its own love (Requirement 4.5).
 */
object OpBlindingJealousy : InfatuationSpellAction {
    override val argc = 3

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result =
        ActionGuard.guard("blinding_jealousy") {
            val jealous = args.getEntity(0, argc)
            val guarded = args.getEntity(1, argc)
            val seconds = args.getPositiveDouble(2, argc)
            env.assertEntityInRange(jealous)
            env.assertEntityInRange(guarded)

            // Requirement 16.10: a broken heart cannot be made jealous — but it can still be guarded,
            // so nothing is asked of the protected one here
            if (BondStore.isHeartbroken(jealous)) throw HexloveMishaps.brokenHeart(jealous)
            if (jealous === guarded) throw HexloveMishaps.selfLove(jealous)
            val marriedJealous = jealous as? net.minecraft.world.entity.player.Player
            val server = jealous.level().server
            if (marriedJealous != null && server != null && !MarriageManager.permitsAffection(server, marriedJealous, guarded.uuid)) {
                val spouse = MarriageManager.spouseOf(server, jealous.uuid)?.let(server.playerList::getPlayer)
                throw HexloveMishaps.boundHeart(jealous, spouse)
            }

            SpellAction.Result(
                Spell(jealous, guarded, seconds),
                MediaCosts.blindingJealousy(seconds),
                listOf(ParticleSpray.burst(jealous.position().add(0.0, jealous.bbHeight / 2.0, 0.0), 1.0)),
            )
        }

    private data class Spell(val jealous: Entity, val guarded: Entity, val seconds: Double) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = ActionGuard.guardCast("blinding_jealousy") {
            val expiresAt = Durations.expiryFromSeconds(
                seconds,
                env.world.gameTime,
                HexloveServerConfig.config.jealousy.maxDurationTicks,
            )
            BondStore.setJealousy(jealous, JealousyBond(guarded.uuid, expiresAt))
            if (jealous is Enemy) {
                (env.castingEntity as? ServerPlayer)?.let {
                    HexloveAdvancements.grant(it, HexloveAdvancements.LOVE_IS_BLIND)
                }
            }

            env.world.sendParticles(
                ParticleTypes.ANGRY_VILLAGER,
                jealous.x, jealous.y + jealous.bbHeight * 0.75, jealous.z,
                5, 0.3, 0.3, 0.3, 0.02,
            )
            // the same arrow and ring the charm draws, in red: from the guarded one to the jealous one
            BondFx.jealousyArrow(env.world, guarded, jealous)
        }
    }
}
