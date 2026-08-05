package io.github.teekas.hexlove.casting.mishaps

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.GarbageIota
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.asTranslatedComponent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.DyeColor

/**
 * Requirement 7.13, revised: pointing a charm at a single creature twice is not "this creature is
 * immune", it is a category error, and it deserves to say so. A heart cannot be handed to itself.
 */
class MishapSelfLove(val entity: Entity) : Mishap() {
    override fun accentColor(ctx: CastingEnvironment, errorCtx: Context): FrozenPigment =
        dyeColor(DyeColor.PINK)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {
        env.mishapEnvironment.yeetHeldItemsTowards(entity.position())
    }

    override fun errorMessage(ctx: CastingEnvironment, errorCtx: Context): Component =
        "hexlove.mishap.self_love".asTranslatedComponent(entity.displayName)
}

/**
 * Requirement 7.8, revised: "expected a positive double, got 0" tells the caster about the type
 * system. This tells them about the spell: there was no feeling to give.
 */
class MishapNoFeeling(val perpetrator: Iota, val reverseIdx: Int) : Mishap() {
    override fun accentColor(ctx: CastingEnvironment, errorCtx: Context): FrozenPigment =
        dyeColor(DyeColor.GRAY)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {
        val index = stack.size - 1 - reverseIdx
        if (index in stack.indices) stack[index] = GarbageIota()
    }

    override fun errorMessage(ctx: CastingEnvironment, errorCtx: Context): Component =
        "hexlove.mishap.no_feeling".asTranslatedComponent(perpetrator.display())
}

/**
 * A heart that has just been broken is not "immune to your magic" — it is grieving, and it will be
 * over. Worth its own sentence, because the caster only has to wait.
 */
class MishapBrokenHeart(val entity: Entity, val beloved: Boolean) : Mishap() {
    override fun accentColor(ctx: CastingEnvironment, errorCtx: Context): FrozenPigment =
        dyeColor(DyeColor.GRAY)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {
        env.mishapEnvironment.yeetHeldItemsTowards(entity.position())
    }

    override fun errorMessage(ctx: CastingEnvironment, errorCtx: Context): Component =
        if (beloved) {
            "hexlove.mishap.broken_heart.beloved".asTranslatedComponent(entity.displayName)
        } else {
            "hexlove.mishap.broken_heart".asTranslatedComponent(entity.displayName)
        }
}

/**
 * Requirement 10, revised: bosses are told apart by the size of their heart, not by a name list, and
 * they are refused outright. [absolute] separates "no feeling reaches this" from "not *this* much".
 */
class MishapTooStrong(val entity: Entity, val absolute: Boolean) : Mishap() {
    override fun accentColor(ctx: CastingEnvironment, errorCtx: Context): FrozenPigment =
        dyeColor(DyeColor.RED)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {
        env.mishapEnvironment.yeetHeldItemsTowards(entity.position())
    }

    override fun errorMessage(ctx: CastingEnvironment, errorCtx: Context): Component =
        if (absolute) {
            "hexlove.mishap.too_strong".asTranslatedComponent(entity.displayName)
        } else {
            "hexlove.mishap.out_of_reach".asTranslatedComponent(entity.displayName)
        }
}

/** The pair would have courted, but the server has that kind of union switched off. */
class MishapForbiddenUnion(val subject: Entity, val master: Entity) : Mishap() {
    override fun accentColor(ctx: CastingEnvironment, errorCtx: Context): FrozenPigment =
        dyeColor(DyeColor.MAGENTA)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {
        env.mishapEnvironment.yeetHeldItemsTowards(subject.position())
    }

    override fun errorMessage(ctx: CastingEnvironment, errorCtx: Context): Component =
        "hexlove.mishap.forbidden_union".asTranslatedComponent(subject.displayName, master.displayName)
}

/** Soulbind has a physical input, so malformed inputs deserve an instruction instead of a Java type key. */
class MishapSoulbindInstruction(private val reason: String) : Mishap() {
    override fun accentColor(ctx: CastingEnvironment, errorCtx: Context): FrozenPigment =
        dyeColor(DyeColor.MAGENTA)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {
        env.castingEntity?.let { env.mishapEnvironment.yeetHeldItemsTowards(it.position()) }
    }

    override fun errorMessage(ctx: CastingEnvironment, errorCtx: Context): Component =
        "hexlove.mishap.soulbind.$reason".asTranslatedComponent
}

/** Succubus' Tithe needs to explain a failed payment, not blame the creature being healed. */
class MishapTithePayment(private val recipient: Entity, private val reason: String) : Mishap() {
    override fun accentColor(ctx: CastingEnvironment, errorCtx: Context): FrozenPigment =
        dyeColor(DyeColor.RED)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {
        env.mishapEnvironment.yeetHeldItemsTowards(recipient.position())
    }

    override fun errorMessage(ctx: CastingEnvironment, errorCtx: Context): Component =
        "hexlove.mishap.tithe.$reason".asTranslatedComponent(recipient.displayName)
}

/** Two people may exchange a breath only through one currently carried, matching vow. */
class MishapBreathExchange(
    private val first: Entity,
    private val second: Entity,
    private val reason: String,
) : Mishap() {
    override fun accentColor(ctx: CastingEnvironment, errorCtx: Context): FrozenPigment =
        dyeColor(DyeColor.LIME)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {
        env.castingEntity?.let { env.mishapEnvironment.yeetHeldItemsTowards(it.position()) }
    }

    override fun errorMessage(ctx: CastingEnvironment, errorCtx: Context): Component =
        "hexlove.mishap.breath_exchange.$reason".asTranslatedComponent(first.displayName, second.displayName)
}
