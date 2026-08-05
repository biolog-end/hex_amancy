package io.github.teekas.hexlove.registry

import io.github.teekas.hexlove.block.VowAltarBlock
import io.github.teekas.hexlove.block.AmethystFlowerBlock
import io.github.teekas.hexlove.block.AmethystHeartBlock
import io.github.teekas.hexlove.block.InfatuationCandleBlock
import io.github.teekas.hexlove.block.PottedAmethystFlowerBlock
import io.github.teekas.hexlove.block.WorldSoulAltarBlock
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object HexloveBlocks : HexloveRegistrar<Block>(
    Registries.BLOCK,
    { BuiltInRegistries.BLOCK },
) {
    val VOW_ALTAR = register("vow_altar") {
        VowAltarBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.QUARTZ)
                .strength(2.5f, 6.0f)
                .sound(SoundType.AMETHYST),
        )
    }

    val AMETHYST_ROSE = register("amethyst_rose") {
        AmethystFlowerBlock(
            moteColour = 0xFF4058,
            properties = flowerProperties(MapColor.COLOR_RED),
        )
    }
    val AMETHYST_DANDELION = register("amethyst_dandelion") {
        AmethystFlowerBlock(
            moteColour = 0xFFE14A,
            properties = flowerProperties(MapColor.COLOR_YELLOW),
        )
    }
    val AMETHYST_TULIP = register("amethyst_tulip") {
        AmethystFlowerBlock(
            moteColour = 0xFF87D2,
            properties = flowerProperties(MapColor.COLOR_PINK),
        )
    }

    // Flower pots have no item form: the ordinary empty pot turns into them when one of our flowers
    // is used on it, exactly like vanilla potted flowers.
    val POTTED_AMETHYST_ROSE = register("potted_amethyst_rose") {
        PottedAmethystFlowerBlock(AMETHYST_ROSE.value, 0xFF4058, potProperties())
    }
    val POTTED_AMETHYST_DANDELION = register("potted_amethyst_dandelion") {
        PottedAmethystFlowerBlock(AMETHYST_DANDELION.value, 0xFFE14A, potProperties())
    }
    val POTTED_AMETHYST_TULIP = register("potted_amethyst_tulip") {
        PottedAmethystFlowerBlock(AMETHYST_TULIP.value, 0xFF87D2, potProperties())
    }

    val AMETHYST_HEART = register("amethyst_heart") {
        AmethystHeartBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_MAGENTA)
                .strength(3.0f, 8.0f)
                .sound(SoundType.AMETHYST)
                .noOcclusion()
                .lightLevel { 9 },
        )
    }
    val CANDLES_OF_INFATUATION = register("candles_of_infatuation") {
        InfatuationCandleBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PINK)
                .strength(0.3f)
                .sound(SoundType.AMETHYST)
                .noOcclusion()
                .noCollission()
                .lightLevel { 9 },
        )
    }
    val WORLD_SOUL_ALTAR = register("world_soul_altar") {
        WorldSoulAltarBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_MAGENTA)
                .strength(5.0f, 12.0f)
                .sound(SoundType.AMETHYST)
                .noOcclusion()
                .lightLevel { state -> if (state.getValue(WorldSoulAltarBlock.ACTIVE)) 14 else 4 },
        )
    }

    private fun flowerProperties(colour: MapColor): BlockBehaviour.Properties =
        BlockBehaviour.Properties.of()
            .mapColor(colour)
            .noCollission()
            .noOcclusion()
            // Vanilla flowers use this property to sit a little differently in every block.
            .offsetType(BlockBehaviour.OffsetType.XZ)
            .instabreak()
            .sound(SoundType.GRASS)
            .lightLevel { 4 }

    private fun potProperties(): BlockBehaviour.Properties =
        BlockBehaviour.Properties.of()
            .instabreak()
            .noOcclusion()
            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
            .lightLevel { 3 }
}
