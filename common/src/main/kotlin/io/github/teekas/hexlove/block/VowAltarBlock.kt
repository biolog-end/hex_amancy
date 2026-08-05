package io.github.teekas.hexlove.block

import io.github.teekas.hexlove.block.entity.VowAltarBlockEntity
import io.github.teekas.hexlove.registry.HexloveBlockEntities
import dev.architectury.registry.menu.MenuRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult

class VowAltarBlock(properties: Properties) : BaseEntityBlock(properties) {
    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = VowAltarBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult {
        if (!level.isClientSide) {
            val altar = level.getBlockEntity(pos) as? VowAltarBlockEntity
            (player as? net.minecraft.server.level.ServerPlayer)?.let { MenuRegistry.openMenu(it, altar ?: return@let) }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>,
    ): BlockEntityTicker<T>? = if (level.isClientSide) null else createTickerHelper(
        type,
        HexloveBlockEntities.VOW_ALTAR.value,
    ) { tickLevel, tickPos, tickState, altar ->
        if (tickLevel is ServerLevel) {
            VowAltarBlockEntity.tick(tickLevel, tickPos, tickState, altar)
        }
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block)) {
            (level.getBlockEntity(pos) as? VowAltarBlockEntity)?.returnRings()
        }
        super.onRemove(state, level, pos, newState, moved)
    }

    private companion object {
        val FACING: DirectionProperty = BlockStateProperties.HORIZONTAL_FACING
    }
}
