@file:JvmName("HexloveAbstractionsImpl")

package io.github.teekas.hexlove.forge

import io.github.teekas.hexlove.registry.HexloveRegistrar
import io.github.teekas.hexlove.registry.HexloveEntities
import io.github.teekas.hexlove.registry.HexloveItems
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraftforge.common.ForgeSpawnEggItem
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import java.util.function.Supplier

fun <T : Any> initRegistry(registrar: HexloveRegistrar<T>) {
    MOD_BUS.addListener { event: RegisterEvent ->
        event.register(registrar.registryKey) { helper ->
            registrar.init(helper::register)
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun createChimeraSpawnEgg(properties: Item.Properties): Item = ForgeSpawnEggItem(
    Supplier { HexloveEntities.CHIMERA.value as EntityType<out Mob> },
    0x5A4638,
    0xD6C8BF,
    properties,
)

fun registerHexCastingCreativeTabItems() {
    val hexCastingTab = ResourceKey.create<CreativeModeTab>(
        Registries.CREATIVE_MODE_TAB,
        ResourceLocation("hexcasting", "hexcasting"),
    )

    // Hex Casting fills this tab at NORMAL priority. LOWEST therefore puts our entries after it.
    MOD_BUS.addListener(EventPriority.LOWEST) { event: BuildCreativeModeTabContentsEvent ->
        if (event.tabKey == hexCastingTab) {
            event.accept(HexloveItems.SOULBOUND_RING.value)
            event.accept(HexloveItems.CHIMERA_SPAWN_EGG.value)
            event.accept(HexloveItems.CHIMERA_MEAT.value)
            event.accept(HexloveItems.COOKED_CHIMERA_MEAT.value)
            event.accept(HexloveItems.RESONANT_MEAT.value)
            event.accept(HexloveItems.STEW_OF_SERENITY.value)
            event.accept(HexloveItems.STEW_OF_FEROCITY.value)
            event.accept(HexloveItems.VOW_ALTAR.value)
            event.accept(HexloveItems.AMETHYST_ROSE.value)
            event.accept(HexloveItems.AMETHYST_DANDELION.value)
            event.accept(HexloveItems.AMETHYST_TULIP.value)
            event.accept(HexloveItems.AMETHYST_HEART.value)
            event.accept(HexloveItems.CANDLES_OF_INFATUATION.value)
            event.accept(HexloveItems.WORLD_SOUL_ALTAR.value)
            event.accept(HexloveItems.CRYSTALLIZED_AFFECTION.value)
        }
    }
}
