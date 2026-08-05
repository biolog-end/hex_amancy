package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationConstMediaAction
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.charm.BreedingSupport

/**
 * Requirement 14: "is this creature ready to mate right now?" Asks and nothing more — the world is
 * not touched, which is why it costs a rounding error.
 */
object OpMatchmakersPurification : InfatuationConstMediaAction {
    override val argc = 1

    override val mediaCost: Long
        get() = MediaCosts.matchmakersPurification()

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> =
        ActionGuard.guard("matchmakers_purification") {
            val target = args.getEntity(0, argc)
            env.assertEntityInRange(target)
            BreedingSupport.isReadyToMate(BreedingSupport.facts(target)).asActionResult
        }
}
