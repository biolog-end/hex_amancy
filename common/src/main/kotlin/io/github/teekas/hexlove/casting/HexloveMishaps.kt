package io.github.teekas.hexlove.casting

import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.api.casting.mishaps.MishapDisallowedSpell
import at.petrak.hexcasting.api.casting.mishaps.MishapEntityTooFarAway
import at.petrak.hexcasting.api.casting.mishaps.MishapImmuneEntity
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.casting.mishaps.MishapBrokenHeart
import io.github.teekas.hexlove.casting.mishaps.MishapBoundHeart
import io.github.teekas.hexlove.casting.mishaps.MishapBreathExchange
import io.github.teekas.hexlove.casting.mishaps.MishapForbiddenUnion
import io.github.teekas.hexlove.casting.mishaps.MishapNoFeeling
import io.github.teekas.hexlove.casting.mishaps.MishapSelfLove
import io.github.teekas.hexlove.casting.mishaps.MishapSoulbindInstruction
import io.github.teekas.hexlove.casting.mishaps.MishapTithePayment
import io.github.teekas.hexlove.casting.mishaps.MishapTooStrong
import net.minecraft.world.entity.Entity

/** Thin wrappers over the stock Hex Casting mishaps, so the mod fails the way Hex fails. */
object HexloveMishaps {
    /** Requirement 1.6. */
    fun incorrectIota(iota: Iota, reverseIdx: Int, expected: String): Mishap =
        MishapInvalidIota.ofType(iota, reverseIdx, expected)

    /** Requirement 2.1. */
    fun outOfAmbit(entity: Entity): Mishap = MishapEntityTooFarAway(entity)

    /** Requirement 10.1 and friends. */
    fun immune(entity: Entity): Mishap = MishapImmuneEntity(entity)

    /** Requirement 1.7. */
    fun disallowed(name: String): Mishap = MishapDisallowedSpell(name)

    /** Requirement 7.13, revised: its own error, not a plain "immune". */
    fun selfLove(entity: Entity): Mishap = MishapSelfLove(entity)

    /** Requirement 7.8, revised: a strength of feeling of zero is not a type error, it is nothing to give. */
    fun noFeeling(iota: Iota, reverseIdx: Int): Mishap = MishapNoFeeling(iota, reverseIdx)

    /** Requirement 18, revised: grief has its own words, and it passes. */
    fun brokenHeart(entity: Entity, beloved: Boolean = false): Mishap = MishapBrokenHeart(entity, beloved)

    /** Requirement 10, revised: a boss is not immune, it is too strong to be moved. */
    fun tooStrong(entity: Entity): Mishap = MishapTooStrong(entity, absolute = true)

    /** The same shape of heart, but only for this much feeling: more would carry it. */
    fun outOfReach(entity: Entity): Mishap = MishapTooStrong(entity, absolute = false)

    fun forbiddenUnion(subject: Entity, master: Entity): Mishap = MishapForbiddenUnion(subject, master)

    fun boundHeart(subject: Entity, spouse: Entity?): Mishap = MishapBoundHeart(subject, spouse)

    fun soulbindInstruction(reason: String): Mishap = MishapSoulbindInstruction(reason)

    fun tithePayment(recipient: Entity, reason: String): Mishap = MishapTithePayment(recipient, reason)

    fun breathExchange(first: Entity, second: Entity, reason: String): Mishap =
        MishapBreathExchange(first, second, reason)
}

/**
 * Requirement 25.4: a mishap travels outward untouched; anything else is logged and converted, so a
 * bug in this addon never crashes the game mid-hex.
 */
object ActionGuard {
    fun <T> guard(what: String, body: () -> T): T = try {
        body()
    } catch (mishap: Mishap) {
        throw mishap
    } catch (t: Throwable) {
        Hexlove.LOGGER.error("Unexpected exception in $what", t)
        throw HexloveMishaps.disallowed(what)
    }

    /** `RenderedSpell.cast` must never throw a mishap, so here we only log. */
    fun guardCast(what: String, body: () -> Unit) {
        try {
            body()
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("Unexpected exception while casting $what", t)
        }
    }
}
