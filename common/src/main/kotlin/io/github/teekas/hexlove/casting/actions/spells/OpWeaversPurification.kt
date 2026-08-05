package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationConstMediaAction
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.world.HexloveWorldData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.level.entity.EntityTypeTest
import java.util.UUID

/** Requirement 17: who is tied to this creature by a thread of fate. */
object FateThreads {
    /** Pure: the identifiers worth resolving, without duplicates and without the subject itself. */
    fun candidates(subject: UUID, haremMembers: Set<UUID>, loveTarget: UUID?, spouse: UUID?): Set<UUID> {
        val out = LinkedHashSet<UUID>()
        out.addAll(haremMembers)
        loveTarget?.let(out::add)
        spouse?.let(out::add)
        out.remove(subject)
        return out
    }
}

/**
 * Requirement 17: only *loaded* entities of the same dimension can be listed — a thread you cannot
 * reach is a thread you cannot see. Heartbroken creatures have no threads at all (Requirement 18.5).
 */
object OpWeaversPurification : InfatuationConstMediaAction {
    override val argc = 1

    override val mediaCost: Long
        get() = MediaCosts.weaversPurification()

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> =
        ActionGuard.guard("weavers_purification") {
            val target = args.getEntity(0, argc)
            env.assertEntityInRange(target)

            if (BondStore.isHeartbroken(target)) {
                return@guard emptyList<Iota>().asActionResult
            }

            val level = env.world
            val worldData = level.server.let(HexloveWorldData::get)
            val marriage = worldData.marriageOf(target.uuid)?.takeIf { it.active }

            val ids = FateThreads.candidates(
                subject = target.uuid,
                haremMembers = worldData.harem(target.uuid).map { it.entity }.toSet(),
                loveTarget = BondStore.love(target)?.target,
                spouse = marriage?.other(target.uuid),
            )

            val found = LinkedHashSet<Entity>()
            for (id in ids) {
                val entity = level.getEntity(id) ?: continue
                if (!entity.isAlive || BondStore.isHeartbroken(entity)) continue
                found.add(entity)
            }
            found.addAll(tamedBy(level, target.uuid))

            found.map { EntityIota(it) as Iota }.asActionResult
        }

    /** Requirement 17.2: vanilla taming is a thread of fate too. */
    private fun tamedBy(level: ServerLevel, owner: UUID): List<Entity> =
        level.getEntities(EntityTypeTest.forClass(TamableAnimal::class.java)) { it.isAlive && it.ownerUUID == owner }
            .filterNot { BondStore.isHeartbroken(it) }
}
