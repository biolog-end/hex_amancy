package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.castables.ConstMediaAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.NullIota
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationConstMediaAction
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.marriage.MarriageManager
import net.minecraft.server.level.ServerPlayer

/** Leaves the stack untouched and places the active, online spouse (or Null) on top. */
object OpHeartsReflection : InfatuationConstMediaAction {
    override val argc = 0
    override val mediaCost: Long get() = MediaCosts.heartsReflection()

    override fun execute(args: List<Iota>, env: CastingEnvironment): List<Iota> =
        ActionGuard.guard("hearts_reflection") {
            val caster = env.castingEntity as? ServerPlayer ?: return@guard listOf(NullIota())
            val server = env.world.server
            val spouse = MarriageManager.spouseOf(server, caster.uuid)
                ?.let(server.playerList::getPlayer)
            // ConstMediaAction expects the values to push. Wrapping this in asActionResult made
            // one extra ListIota, so Heart's Reflection used to return a one-element list instead
            // of the spouse themselves.
            listOf(if (spouse == null) NullIota() else EntityIota(spouse))
        }
}
