package io.github.teekas.hexlove.casting.mishaps

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.mishaps.Mishap
import at.petrak.hexcasting.api.pigment.FrozenPigment
import at.petrak.hexcasting.api.utils.asTranslatedComponent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.DyeColor

/** A vowed heart is not generally immune to love: it has already made one precise promise. */
class MishapBoundHeart(val subject: Entity, val spouse: Entity?) : Mishap() {
    override fun accentColor(ctx: CastingEnvironment, errorCtx: Context): FrozenPigment = dyeColor(DyeColor.LIGHT_BLUE)

    override fun execute(env: CastingEnvironment, errorCtx: Context, stack: MutableList<Iota>) {
        env.mishapEnvironment.yeetHeldItemsTowards(subject.position())
    }

    override fun errorMessage(ctx: CastingEnvironment, errorCtx: Context): Component =
        if (spouse == null) {
            "hexlove.mishap.bound_heart".asTranslatedComponent(subject.displayName)
        } else {
            "hexlove.mishap.bound_heart.with_spouse".asTranslatedComponent(subject.displayName, spouse.displayName)
        }
}
