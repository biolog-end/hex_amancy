package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.bond.isActiveAt
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationConstMediaAction
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.world.HexloveWorldData
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.LivingEntity

/**
 * A one-iota-to-one-iota action: it measures the vitality of every creature devoted to a target.
 *
 * This deliberately follows the same harem registry as [OpSuccubusTithe]. A loaded lover has an
 * exact, current health value; an unloaded lover contributes the latest conservative observation
 * kept by the registry. Deferred Tithe damage is already reserved, so it is excluded from the
 * result. Thus this purification reports exactly the health a new Tithe may safely request,
 * without loading chunks or pretending that an unseen creature can be inspected directly.
 */
object OpHeartsPurification : InfatuationConstMediaAction {
    override val argc = 1

    // It is the same small, read-only operation as Weaver's Purification.
    override val mediaCost: Long
        get() = MediaCosts.weaversPurification()

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> =
        ActionGuard.guard("hearts_purification") {
            val beloved = args.getEntity(0, argc)
            env.assertEntityInRange(beloved)

            val data = HexloveWorldData.get(env.world.server)
            var total = 0.0
            val stale = ArrayList<java.util.UUID>()
            for (member in data.harem(beloved.uuid)) {
                if (!member.isLoveActiveAt(env.world.gameTime)) {
                    stale += member.entity
                    continue
                }
                val level = env.world.server.getLevel(ResourceKey.create(Registries.DIMENSION, member.dimension))
                val entity = level?.getEntity(member.entity)

                // The registry is authoritative for absent lovers. Recheck a present one so a
                // stale entry can never count somebody whose feelings have since changed.
                val health = when (entity) {
                    null -> member.lastKnownHealth.toDouble().coerceAtLeast(0.0)
                    is LivingEntity -> if (entity.isAlive && BondStore.love(entity)?.let {
                            it.target == beloved.uuid && it.isActiveAt(env.world.gameTime)
                        } == true
                    ) {
                        entity.health.toDouble().coerceAtLeast(0.0)
                    } else {
                        stale += member.entity
                        0.0
                    }
                    else -> {
                        stale += member.entity
                        0.0
                    }
                }
                total += (health - data.debtOf(member.entity)).coerceAtLeast(0.0)
            }
            stale.forEach { data.removeHaremMember(beloved.uuid, it) }
            total.asActionResult
        }
}
