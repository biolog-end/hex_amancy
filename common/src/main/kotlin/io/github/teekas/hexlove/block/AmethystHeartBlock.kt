package io.github.teekas.hexlove.block

import io.github.teekas.hexlove.block.entity.AmethystHeartBlockEntity
import io.github.teekas.hexlove.registry.HexloveBlockEntities
import dev.architectury.registry.menu.MenuRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/** A physical reservoir that firmly clings to the face on which it was placed. */
class AmethystHeartBlock(properties: Properties) : BaseEntityBlock(properties) {
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(ATTACHMENT, Direction.UP)
                .setValue(FACING, Direction.NORTH),
        )
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = AmethystHeartBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

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

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = shapeFor(state)

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = shapeFor(state)

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(ATTACHMENT, FACING)
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
            val heart = level.getBlockEntity(pos) as? AmethystHeartBlockEntity
            (player as? ServerPlayer)?.let { MenuRegistry.openMenu(it, heart ?: return@let) }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block) && level is ServerLevel) {
            val dust = (level.getBlockEntity(pos) as? AmethystHeartBlockEntity)?.takeDustForDrop()
            if (dust != null && !dust.isEmpty) popResource(level, pos, dust)
        }
        super.onRemove(state, level, pos, newState, moved)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>,
    ): BlockEntityTicker<T>? = if (level.isClientSide) null else createTickerHelper(
        type,
        HexloveBlockEntities.AMETHYST_HEART.value,
    ) { tickLevel, tickPos, tickState, heart ->
        if (tickLevel is ServerLevel) AmethystHeartBlockEntity.tick(tickLevel, tickPos, tickState, heart)
    }

    /**
     * The face the Heart's mouth points at: its horizontal rotation when it stands on a floor or
     * hangs from a ceiling, or the wall it clings to. The block owns this because only it knows
     * how [ATTACHMENT] and [FACING] combine.
     */
    fun mouthDirection(state: BlockState): Direction {
        val attachment = state.getValue(ATTACHMENT)
        return if (attachment.axis.isVertical) state.getValue(FACING) else attachment
    }

    private companion object {
        /** The face pointing away from the sturdy block supporting the Heart. */
        val ATTACHMENT: DirectionProperty = DirectionProperty.create("attachment")
        /** Horizontal orientation chosen from the placing mage's look direction. */
        val FACING: DirectionProperty = BlockStateProperties.HORIZONTAL_FACING

        /**
         * A stepped approximation of the supplied model's solid body. Tiny decorative thorns
         * remain non-colliding, while the obsidian foot, gold cradle and both heart lobes follow
         * their visible bounds. The topmost crystal reaches one model unit outside the block.
         */
        val UPRIGHT_COLLISION = listOf(
            CollisionBox(2.0, 0.0, 1.9, 14.0, 3.0, 14.0),
            CollisionBox(3.5, 3.0, 3.35, 12.6, 4.2, 12.45),
            CollisionBox(5.6, 3.7, 4.6, 10.4, 4.2, 11.4),
            CollisionBox(5.1, 4.2, 4.6, 10.9, 5.7, 11.4),
            CollisionBox(4.6, 5.7, 4.6, 11.4, 6.7, 11.4),
            CollisionBox(3.6, 6.7, 4.6, 12.4, 7.7, 11.4),
            CollisionBox(2.6, 7.7, 4.6, 13.4, 9.7, 11.4),
            CollisionBox(1.4, 9.7, 4.6, 14.6, 13.2, 11.4),
            CollisionBox(2.6, 13.2, 4.6, 6.9, 14.7, 11.4),
            CollisionBox(9.1, 13.2, 4.6, 13.4, 14.7, 11.4),
            CollisionBox(3.1, 14.7, 5.7, 6.4, 15.2, 10.3),
            CollisionBox(9.6, 14.7, 5.7, 12.9, 15.2, 10.3),
            CollisionBox(3.6, 15.2, 5.8, 5.9, 15.7, 9.9),
            CollisionBox(10.1, 15.2, 5.8, 12.4, 15.7, 9.9),
            CollisionBox(4.1, 15.7, 6.1, 5.4, 16.2, 9.4),
            CollisionBox(10.6, 15.7, 6.1, 11.9, 16.2, 9.4),
            CollisionBox(4.35, 16.2, 8.3, 5.15, 17.0, 9.4),
            CollisionBox(11.1, 16.2, 8.3, 11.9, 17.0, 9.4),
        )

        val SHAPES: Map<Pair<Direction, Direction>, VoxelShape> = buildMap {
            for (attachment in Direction.entries) {
                for (facing in Direction.Plane.HORIZONTAL) {
                    put(attachment to facing, createShape(attachment, facing))
                }
            }
        }

        fun shapeFor(state: BlockState): VoxelShape = SHAPES.getValue(
            state.getValue(ATTACHMENT) to state.getValue(FACING),
        )

        fun createShape(attachment: Direction, facing: Direction): VoxelShape {
            val xRotation: Int
            val yRotation: Int
            when (attachment) {
                Direction.UP -> {
                    xRotation = 0
                    yRotation = horizontalRotation(facing)
                }

                Direction.DOWN -> {
                    xRotation = 180
                    yRotation = horizontalRotation(facing)
                }

                Direction.NORTH -> {
                    xRotation = 90
                    yRotation = 0
                }

                Direction.SOUTH -> {
                    xRotation = 90
                    yRotation = 180
                }

                Direction.EAST -> {
                    xRotation = 90
                    yRotation = 90
                }

                Direction.WEST -> {
                    xRotation = 90
                    yRotation = 270
                }
            }

            var result = Shapes.empty()
            for (part in UPRIGHT_COLLISION) {
                val rotated = part.rotate(xRotation, yRotation)
                result = Shapes.or(
                    result,
                    box(rotated.minX, rotated.minY, rotated.minZ, rotated.maxX, rotated.maxY, rotated.maxZ),
                )
            }
            return result.optimize()
        }

        fun horizontalRotation(facing: Direction): Int = when (facing) {
            Direction.NORTH -> 0
            Direction.EAST -> 90
            Direction.SOUTH -> 180
            Direction.WEST -> 270
            else -> error("Amethyst Heart facing must be horizontal: $facing")
        }

        data class CollisionBox(
            val minX: Double,
            val minY: Double,
            val minZ: Double,
            val maxX: Double,
            val maxY: Double,
            val maxZ: Double,
        ) {
            fun rotate(xRotation: Int, yRotation: Int): CollisionBox {
                val corners = buildList {
                    for (x in listOf(minX, maxX)) {
                        for (y in listOf(minY, maxY)) {
                            for (z in listOf(minZ, maxZ)) {
                                add(rotatePoint(x, y, z, xRotation, yRotation))
                            }
                        }
                    }
                }
                return CollisionBox(
                    corners.minOf { it[0] },
                    corners.minOf { it[1] },
                    corners.minOf { it[2] },
                    corners.maxOf { it[0] },
                    corners.maxOf { it[1] },
                    corners.maxOf { it[2] },
                )
            }

            private fun rotatePoint(
                x: Double,
                y: Double,
                z: Double,
                xRotation: Int,
                yRotation: Int,
            ): DoubleArray {
                var rotatedX = x - 8.0
                var rotatedY = y - 8.0
                var rotatedZ = z - 8.0

                repeat(xRotation / 90) {
                    val nextY = rotatedZ
                    val nextZ = -rotatedY
                    rotatedY = nextY
                    rotatedZ = nextZ
                }
                repeat(yRotation / 90) {
                    val nextX = -rotatedZ
                    val nextZ = rotatedX
                    rotatedX = nextX
                    rotatedZ = nextZ
                }
                return doubleArrayOf(rotatedX + 8.0, rotatedY + 8.0, rotatedZ + 8.0)
            }
        }
    }
}
