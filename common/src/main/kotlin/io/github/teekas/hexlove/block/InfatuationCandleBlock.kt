package io.github.teekas.hexlove.block

import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.RandomSource
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

/** Pink crystalline candles that turn to greet the mage who places them. */
class InfatuationCandleBlock(properties: BlockBehaviour.Properties) : Block(properties) {
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(ATTACHMENT, Direction.UP)
                .setValue(FACING, Direction.NORTH)
        )
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState()
            .setValue(ATTACHMENT, context.clickedFace)
            .setValue(FACING, context.horizontalDirection.opposite)

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        val attachment = state.getValue(ATTACHMENT)
        val supportPos = pos.relative(attachment.opposite)
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, attachment)
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighbourState: BlockState,
        level: LevelAccessor,
        pos: BlockPos,
        neighbourPos: BlockPos,
    ): BlockState = if (direction == state.getValue(ATTACHMENT).opposite && !state.canSurvive(level, pos)) {
        Blocks.AIR.defaultBlockState()
    } else {
        super.updateShape(state, direction, neighbourState, level, pos, neighbourPos)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(ATTACHMENT, FACING)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        SHAPES.getValue(state.getValue(ATTACHMENT))

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        // One quiet mote keeps the crystal alive without turning a room of candles into a particle
        // cloud. Client-side spawning also keeps the effect network-free.
        repeat(1) {
            level.addParticle(
                if (random.nextBoolean()) PINK else VIOLET,
                pos.x + 0.18 + random.nextDouble() * 0.64,
                pos.y + 0.24 + random.nextDouble() * 0.74,
                pos.z + 0.18 + random.nextDouble() * 0.64,
                (random.nextDouble() - 0.5) * 0.025,
                0.018 + random.nextDouble() * 0.030,
                (random.nextDouble() - 0.5) * 0.025,
            )
        }
    }

    companion object {
        val ATTACHMENT: DirectionProperty = DirectionProperty.create("attachment")
        private val FACING: DirectionProperty = BlockStateProperties.HORIZONTAL_FACING
        /**
         * The model is rotated for each attachment face, so its collision and selection shape
         * must rotate with it.  A fixed upright box made wall-mounted candles act as though
         * they were still standing on the floor.
         */
        val SHAPES = mapOf(
            Direction.UP to box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0),
            Direction.DOWN to box(2.0, 3.0, 2.0, 14.0, 16.0, 14.0),
            Direction.NORTH to box(2.0, 2.0, 3.0, 14.0, 14.0, 16.0),
            Direction.SOUTH to box(2.0, 2.0, 0.0, 14.0, 14.0, 13.0),
            Direction.EAST to box(0.0, 2.0, 2.0, 13.0, 14.0, 14.0),
            Direction.WEST to box(3.0, 2.0, 2.0, 16.0, 14.0, 14.0),
        )
        val PINK = ConjureParticleOptions(0xFF6FAF)
        val VIOLET = ConjureParticleOptions(0xEBA4FF)
    }
}
