package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.getList
import at.petrak.hexcasting.api.casting.getPositiveDouble
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.bond.isActiveAt
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationSpellAction
import io.github.teekas.hexlove.casting.HexloveMishaps
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.casting.tithe.TithePlan
import io.github.teekas.hexlove.casting.tithe.TithePlanner
import io.github.teekas.hexlove.casting.tithe.TitheSource
import io.github.teekas.hexlove.fx.BondFx
import io.github.teekas.hexlove.world.HaremMember
import io.github.teekas.hexlove.world.HexloveWorldData
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import java.util.UUID

/**
 * The other Great Spell: it follows an already-made Love_Bond rather than reaching with the
 * recipient's hand. That is why neither an explicit source nor a whole harem is checked against
 * ambit: the spell reaches only along an already-made Love_Bond.
 */
object OpSuccubusTithe : InfatuationSpellAction {
    override val argc = 3

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result =
        ActionGuard.guard("succubus_tithe") {
            val recipient = args.getEntity(0, argc) as? LivingEntity
                ?: throw HexloveMishaps.incorrectIota(args[0], 2, "entity.living")
            env.assertEntityInRange(recipient)
            val requestedSources = args.getList(1, argc).toList()
            val requested = args.getPositiveDouble(2, argc)
            val data = HexloveWorldData.get(env.world.server)

            val sources = if (requestedSources.isEmpty()) {
                allHaremSources(env.world, recipient, data.harem(recipient.uuid), data)
            } else {
                explicitSources(requestedSources, recipient, data, env.world.gameTime)
            }
            val plan = TithePlanner.distribute(
                requested = requested,
                sources = sources.values.map { it.snapshot },
            )
            // This is deliberately the requested amount, not the recipient's missing health.
            // Healing past full health is harmlessly discarded by LivingEntity.heal, but every
            // selected heart still pays its exact planned share.
            if (requested > PAYMENT_EPSILON && plan.total + PAYMENT_EPSILON < requested) {
                val reason = if (sources.isEmpty()) "no_sources" else "insufficient_health"
                throw HexloveMishaps.tithePayment(recipient, reason)
            }

            SpellAction.Result(
                Spell(recipient, plan, sources),
                MediaCosts.succubusTithe(),
                listOf(ParticleSpray.cloud(recipient.position().add(0.0, recipient.bbHeight * 0.5, 0.0), 1.0)),
            )
        }

    private data class ResolvedSource(val snapshot: TitheSource, val entity: LivingEntity?)

    private const val PAYMENT_EPSILON = 1.0e-6

    private fun explicitSources(
        iotas: List<Iota>,
        recipient: LivingEntity,
        data: HexloveWorldData,
        now: Long,
    ): Map<UUID, ResolvedSource> {
        val out = LinkedHashMap<UUID, ResolvedSource>()
        for ((index, iota) in iotas.withIndex()) {
            val entity = (iota as? EntityIota)?.entity
                ?: throw HexloveMishaps.incorrectIota(iota, argc - 1 - index, "an entity")
            val living = entity as? LivingEntity
                ?: throw HexloveMishaps.incorrectIota(iota, argc - 1 - index, "entity.living")
            if (!living.isAlive || !isActiveLover(living, recipient, now)) {
                throw HexloveMishaps.tithePayment(recipient, "unbound_source")
            }
            out[living.uuid] = source(living, recipient, data)
        }
        return out
    }

    private fun allHaremSources(
        level: ServerLevel,
        recipient: LivingEntity,
        members: Set<HaremMember>,
        data: HexloveWorldData,
    ): Map<UUID, ResolvedSource> {
        val out = LinkedHashMap<UUID, ResolvedSource>()
        val stale = ArrayList<UUID>()
        for (member in members) {
            // A temporary charm can end while its owner is unloaded. The expiry kept in the
            // registry makes this check meaningful without loading the creature's chunk.
            if (!member.isLoveActiveAt(level.gameTime)) {
                stale += member.entity
                continue
            }
            val sourceLevel = level.server.getLevel(ResourceKey.create(Registries.DIMENSION, member.dimension))
            val living = sourceLevel?.getEntity(member.entity) as? LivingEntity
            if (living != null) {
                if (living.isAlive && isActiveLover(living, recipient, level.gameTime)) {
                    out[member.entity] = source(living, recipient, data)
                } else {
                    stale += member.entity
                }
                continue
            }
            // HaremMember stores the last observed health before an entity unloaded. A source with
            // no trustworthy observation contributes nothing rather than creating an impossible debt.
            if (member.lastKnownHealth > 0f) {
                out[member.entity] = ResolvedSource(
                    TitheSource(member.entity, member.lastKnownHealth.toDouble(), data.debtOf(member.entity).toDouble(), false, Double.MAX_VALUE),
                    null,
                )
            }
        }
        stale.forEach { data.removeHaremMember(recipient.uuid, it) }
        return out
    }

    private fun isActiveLover(source: LivingEntity, recipient: LivingEntity, now: Long): Boolean =
        BondStore.love(source)?.let { it.target == recipient.uuid && it.isActiveAt(now) } == true

    private fun source(entity: LivingEntity, recipient: LivingEntity, data: HexloveWorldData): ResolvedSource =
        ResolvedSource(
            TitheSource(
                entity = entity.uuid,
                health = entity.health.toDouble(),
                existingDebt = data.debtOf(entity.uuid).toDouble(),
                loaded = true,
                distanceSqr = entity.distanceToSqr(recipient),
            ),
            entity,
        )

    private data class Spell(
        val recipient: LivingEntity,
        val plan: TithePlan,
        val sources: Map<UUID, ResolvedSource>,
    ) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = ActionGuard.guardCast("succubus_tithe") {
            if (!recipient.isAlive) return@guardCast
            val data = HexloveWorldData.get(env.world.server)
            var paidHealth = 0.0

            for (portion in plan.portions) {
                val resolved = sources[portion.entity] ?: continue
                val amount = portion.amount.toFloat()
                if (amount <= 0f) continue
                val source = resolved.entity
                if (source != null && source.isAlive && source.level() === env.world) {
                    // Never credit an immediate source before the game has actually accepted its
                    // damage. Invulnerability, a cancelled hurt event, or an absorbed hit cannot
                    // become free healing for the recipient.
                    val before = source.health
                    source.hurt(source.damageSources().magic(), amount)
                    val actuallyPaid = (before - source.health).coerceAtLeast(0f)
                    if (actuallyPaid > 0f) {
                        paidHealth += actuallyPaid
                        BondFx.titheDrain(env.world, source, recipient)
                    }
                    val unpaid = amount - actuallyPaid
                    if (unpaid > PAYMENT_EPSILON.toFloat() && source.isAlive) {
                        data.addDebt(portion.entity, unpaid, recipient.uuid, env.world.gameTime)
                    }
                } else {
                    // An absent source pays exactly once on its next load. The server retains the
                    // obligation even if the recipient dies or leaves first; in that case the
                    // eventual healing simply burns away.
                    data.addDebt(portion.entity, amount, recipient.uuid, env.world.gameTime)
                }
            }
            if (paidHealth > SUCCUBUS_ADVANCEMENT_HEALTH) {
                (env.castingEntity as? ServerPlayer)?.let {
                    HexloveAdvancements.grant(it, HexloveAdvancements.SUCCUBUS)
                }
            }
            if (paidHealth > 0.0) {
                // The ritual's intensity follows health actually restored, not health burned by
                // over-healing a full recipient. This also keeps the visual honest when another
                // effect changes the recipient's health during the same server tick.
                val beforeHeal = recipient.health
                recipient.heal(paidHealth.toFloat())
                val restored = (recipient.health - beforeHeal).coerceAtLeast(0f)
                if (restored > 0f) {
                    BondFx.titheHealing(env.world, recipient, restored)
                }
            }
        }
    }

    private const val SUCCUBUS_ADVANCEMENT_HEALTH = 20.0f
}
