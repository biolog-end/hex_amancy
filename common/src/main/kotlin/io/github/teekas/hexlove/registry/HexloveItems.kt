package io.github.teekas.hexlove.registry

import io.github.teekas.hexlove.createChimeraSpawnEgg
import io.github.teekas.hexlove.item.CookedChimeraMeatItem
import io.github.teekas.hexlove.item.NatureStewItem
import io.github.teekas.hexlove.item.SoulboundRingItem
import io.github.teekas.hexlove.item.LoreBlockItem
import io.github.teekas.hexlove.item.CrystallizedAffectionItem
import io.github.teekas.hexlove.nature.ChimeraNature
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.minecraft.world.item.BlockItem
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Rarity

/** Things a player can hold. Kept separate from entities so both loaders register them in the
 * ordinary vanilla item registry. */
object HexloveItems : HexloveRegistrar<Item>(
    Registries.ITEM,
    { BuiltInRegistries.ITEM },
) {
    /** Crafted inert; it becomes visibly awake only after [Soulbind]. */
    val SOULBOUND_RING = register("soulbound_ring") { SoulboundRingItem(Item.Properties().stacksTo(1)) }
    /** Unsafe raw flesh from a creature made of two incompatible bloodlines. */
    val CHIMERA_MEAT = register("chimera_meat") {
        Item(Item.Properties().food(
            FoodProperties.Builder()
                .nutrition(2)
                .saturationMod(0.2f)
                .effect(MobEffectInstance(MobEffects.CONFUSION, 200), 1.0f)
                .effect(MobEffectInstance(MobEffects.HUNGER, 300), 1.0f)
                .build(),
        ))
    }
    /** A chicken-sized dinner with a deliberately unreliable magical aftertaste. */
    val COOKED_CHIMERA_MEAT = register("cooked_chimera_meat") {
        CookedChimeraMeatItem(Item.Properties().food(
            FoodProperties.Builder().nutrition(6).saturationMod(0.6f).build(),
        ))
    }
    /**
     * Chimera meat that soaked up a whole dust of affection inside an [AmethystHeartBlockEntity].
     * The body reads it as an overdose of raw emotion: it feeds like roast chicken and then
     * makes the eater glow with somebody else's love while their head refuses to digest it.
     */
    val RESONANT_MEAT = register("resonant_meat") {
        Item(Item.Properties().rarity(Rarity.UNCOMMON).food(
            FoodProperties.Builder()
                .nutrition(6)
                .saturationMod(0.6f)
                .effect(MobEffectInstance(MobEffects.CONFUSION, 300), 1.0f)
                .effect(MobEffectInstance(MobEffects.GLOWING, 600), 1.0f)
                .effect(MobEffectInstance(MobEffects.POISON, 40), 1.0f)
                .effect(MobEffectInstance(MobEffects.HUNGER, 200), 1.0f)
                .build(),
        ))
    }
    /** Spawns the same base-size, 20-health adult as `/summon hexlove:chimera`. */
    val CHIMERA_SPAWN_EGG = register("chimera_spawn_egg") {
        createChimeraSpawnEgg(Item.Properties())
    }
    /** Grants serene (herbivore) nature for the configured duration. Returns the bowl. */
    val STEW_OF_SERENITY = register("stew_of_serenity") {
        NatureStewItem(
            ChimeraNature.SERENE,
            Item.Properties().stacksTo(1).food(
                FoodProperties.Builder().nutrition(9).saturationMod(0.6f).build(),
            ),
        )
    }
    /** Grants ferocious (ghoul) nature for the configured duration. Returns the bowl. */
    val STEW_OF_FEROCITY = register("stew_of_ferocity") {
        NatureStewItem(
            ChimeraNature.FEROCIOUS,
            Item.Properties().stacksTo(1).food(
                FoodProperties.Builder().nutrition(9).saturationMod(0.6f).build(),
            ),
        )
    }
    val VOW_ALTAR = register("vow_altar") { BlockItem(HexloveBlocks.VOW_ALTAR.value, Item.Properties()) }
    val AMETHYST_ROSE = register("amethyst_rose") {
        LoreBlockItem(HexloveBlocks.AMETHYST_ROSE.value, "block.hexlove.amethyst_rose.tooltip", 0xB38EF3)
    }
    val AMETHYST_DANDELION = register("amethyst_dandelion") {
        LoreBlockItem(HexloveBlocks.AMETHYST_DANDELION.value, "block.hexlove.amethyst_dandelion.tooltip", 0xB38EF3)
    }
    val AMETHYST_TULIP = register("amethyst_tulip") {
        LoreBlockItem(HexloveBlocks.AMETHYST_TULIP.value, "block.hexlove.amethyst_tulip.tooltip", 0xB38EF3)
    }
    val AMETHYST_HEART = register("amethyst_heart") {
        LoreBlockItem(HexloveBlocks.AMETHYST_HEART.value, "block.hexlove.amethyst_heart.tooltip", 0xC995FF)
    }
    val CANDLES_OF_INFATUATION = register("candles_of_infatuation") {
        LoreBlockItem(HexloveBlocks.CANDLES_OF_INFATUATION.value, "block.hexlove.candles_of_infatuation.tooltip", 0xFF9ED8)
    }
    val WORLD_SOUL_ALTAR = register("world_soul_altar") {
        LoreBlockItem(HexloveBlocks.WORLD_SOUL_ALTAR.value, "block.hexlove.world_soul_altar.tooltip", 0xD98BFF)
    }
    val CRYSTALLIZED_AFFECTION = register("crystallized_affection") {
        CrystallizedAffectionItem(Item.Properties().rarity(Rarity.RARE))
    }
}
