package io.github.teekas.hexlove.registry

import io.github.teekas.hexlove.menu.AmethystHeartMenu
import io.github.teekas.hexlove.menu.VowAltarMenu
import io.github.teekas.hexlove.menu.WorldSoulAltarMenu
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.inventory.MenuType

/** Server-side containers. The screen itself is registered only from HexloveClient. */
object HexloveMenus : HexloveRegistrar<MenuType<*>>(
    Registries.MENU,
    { BuiltInRegistries.MENU },
) {
    val VOW_ALTAR = register("vow_altar") {
        MenuType(::VowAltarMenu, FeatureFlags.VANILLA_SET)
    }
    val AMETHYST_HEART = register("amethyst_heart") {
        MenuType(::AmethystHeartMenu, FeatureFlags.VANILLA_SET)
    }
    val WORLD_SOUL_ALTAR = register("world_soul_altar") {
        MenuType(::WorldSoulAltarMenu, FeatureFlags.VANILLA_SET)
    }
}
