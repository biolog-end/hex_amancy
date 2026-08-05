@file:JvmName("HexloveAbstractionsImpl")

package io.github.teekas.hexlove.fabric

import dev.architectury.registry.CreativeTabRegistry
import io.github.teekas.hexlove.registry.HexloveRegistrar
import io.github.teekas.hexlove.registry.HexloveEntities
import io.github.teekas.hexlove.registry.HexloveItems
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.SpawnEggItem
import java.util.function.Supplier

fun <T : Any> initRegistry(registrar: HexloveRegistrar<T>) {
    val registry = registrar.registry
    registrar.init { id, value -> Registry.register(registry, id, value) }
}

@Suppress("UNCHECKED_CAST")
fun createChimeraSpawnEgg(properties: Item.Properties): Item = SpawnEggItem(
    HexloveEntities.CHIMERA.value as EntityType<out Mob>,
    0x5A4638,
    0xD6C8BF,
    properties,
)

fun registerHexCastingCreativeTabItems() {
    CreativeTabRegistry.append(
        ResourceKey.create<CreativeModeTab>(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation("hexcasting", "hexcasting"),
        ),
        Supplier { HexloveItems.SOULBOUND_RING.value },
        Supplier { HexloveItems.CHIMERA_SPAWN_EGG.value },
        Supplier { HexloveItems.CHIMERA_MEAT.value },
        Supplier { HexloveItems.COOKED_CHIMERA_MEAT.value },
        Supplier { HexloveItems.RESONANT_MEAT.value },
        Supplier { HexloveItems.STEW_OF_SERENITY.value },
        Supplier { HexloveItems.STEW_OF_FEROCITY.value },
        Supplier { HexloveItems.VOW_ALTAR.value },
        Supplier { HexloveItems.AMETHYST_ROSE.value },
        Supplier { HexloveItems.AMETHYST_DANDELION.value },
        Supplier { HexloveItems.AMETHYST_TULIP.value },
        Supplier { HexloveItems.AMETHYST_HEART.value },
        Supplier { HexloveItems.CANDLES_OF_INFATUATION.value },
        Supplier { HexloveItems.WORLD_SOUL_ALTAR.value },
        Supplier { HexloveItems.CRYSTALLIZED_AFFECTION.value },
    )
}
