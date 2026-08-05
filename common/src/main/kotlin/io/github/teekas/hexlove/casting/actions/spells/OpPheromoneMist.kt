package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getPositiveDouble
import at.petrak.hexcasting.api.casting.getVec3
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.bond.Durations
import io.github.teekas.hexlove.bond.PheromoneState
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationSpellAction
import io.github.teekas.hexlove.casting.HexloveMishaps
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.fx.BondFx
import io.github.teekas.hexlove.networking.msg.MsgPheromoneTouchS2C
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * A heart held inside a circle: a cloud of perfume rather than a bond. Hostile creatures with no
 * existing attachment become peaceful, but one hit tears the fragile calm apart.
 */
object OpPheromoneMist : InfatuationSpellAction {
    override val argc = 3

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result =
        ActionGuard.guard("pheromone_mist") {
            val centre = args.getVec3(0, argc)
            val requestedRadius = args.getPositiveDouble(1, argc)
            val seconds = args.getPositiveDouble(2, argc)
            env.assertVecInRange(centre)
            if (!HexloveServerConfig.config.toggles.allowPheromoneMist) {
                throw HexloveMishaps.disallowed("pheromone_mist")
            }

            val radius = requestedRadius.coerceAtMost(HexloveServerConfig.config.pheromones.maxRadius)
            SpellAction.Result(
                Spell(centre, radius, seconds),
                MediaCosts.pheromoneMist(radius, seconds),
                listOf(ParticleSpray.cloud(centre, radius.coerceAtLeast(0.5))),
            )
        }

    private data class Spell(val centre: Vec3, val radius: Double, val seconds: Double) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = ActionGuard.guardCast("pheromone_mist") {
            val expiresAt = Durations.expiryFromSeconds(
                seconds,
                env.world.gameTime,
                HexloveServerConfig.config.pheromones.maxDurationTicks,
            )
            val area = AABB.ofSize(centre, radius * 2.0, radius * 2.0, radius * 2.0)
            val mobs = env.world.getEntitiesOfClass(Mob::class.java, area) {
                it is Enemy && it.isAlive && it.distanceToSqr(centre) <= radius * radius && BondStore.isUnattached(it)
            }
            val state = PheromoneState(expiresAt)
            for (mob in mobs) BondStore.setPheromone(mob, state)
            if (mobs.isNotEmpty()) {
                (env.castingEntity as? ServerPlayer)?.let {
                    HexloveAdvancements.grant(it, HexloveAdvancements.LOVE_IS_IN_THE_AIR)
                }
            }

            // Players are never pacified: they receive only a quick sensory flash of the spell.
            val touchedPlayers = env.world.players().filter { it.isAlive && it.distanceToSqr(centre) <= radius * radius }
            MsgPheromoneTouchS2C().sendToPlayers(touchedPlayers)
            BondFx.pheromoneBurst(env.world, centre, radius)
            BondFx.pheromoneCloud(env.world, centre, radius, env.world.gameTime, expiresAt)
        }
    }
}
