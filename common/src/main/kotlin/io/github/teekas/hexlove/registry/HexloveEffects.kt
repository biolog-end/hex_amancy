package io.github.teekas.hexlove.registry

import io.github.teekas.hexlove.effect.FerociousChimeraEffect
import io.github.teekas.hexlove.effect.HumiliationEffect
import io.github.teekas.hexlove.effect.SereneChimeraEffect
import io.github.teekas.hexlove.effect.WorldsEmbraceEffect
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.effect.MobEffect

object HexloveEffects : HexloveRegistrar<MobEffect>(
    Registries.MOB_EFFECT,
    { BuiltInRegistries.MOB_EFFECT },
) {
    val WORLDS_EMBRACE = register("worlds_embrace", ::WorldsEmbraceEffect)
    val SERENE_CHIMERA = register("serene_chimera", ::SereneChimeraEffect)
    val FEROCIOUS_CHIMERA = register("ferocious_chimera", ::FerociousChimeraEffect)
    val HUMILIATION = register("humiliation", ::HumiliationEffect)
}
