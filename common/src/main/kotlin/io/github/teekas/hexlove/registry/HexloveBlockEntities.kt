package io.github.teekas.hexlove.registry

import io.github.teekas.hexlove.block.entity.VowAltarBlockEntity
import io.github.teekas.hexlove.block.entity.AmethystHeartBlockEntity
import io.github.teekas.hexlove.block.entity.WorldSoulAltarBlockEntity
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType

object HexloveBlockEntities : HexloveRegistrar<BlockEntityType<*>>(
    Registries.BLOCK_ENTITY_TYPE,
    { BuiltInRegistries.BLOCK_ENTITY_TYPE },
) {
    val VOW_ALTAR = register("vow_altar") {
        BlockEntityType.Builder.of(::VowAltarBlockEntity, HexloveBlocks.VOW_ALTAR.value).build(null)
    }
    val AMETHYST_HEART = register("amethyst_heart") {
        BlockEntityType.Builder.of(::AmethystHeartBlockEntity, HexloveBlocks.AMETHYST_HEART.value).build(null)
    }
    val WORLD_SOUL_ALTAR = register("world_soul_altar") {
        BlockEntityType.Builder.of(::WorldSoulAltarBlockEntity, HexloveBlocks.WORLD_SOUL_ALTAR.value).build(null)
    }
}
