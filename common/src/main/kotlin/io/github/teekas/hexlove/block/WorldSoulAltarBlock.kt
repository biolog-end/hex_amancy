package io.github.teekas.hexlove.block

import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import dev.architectury.registry.menu.MenuRegistry
import io.github.teekas.hexlove.block.entity.WorldSoulAltarBlockEntity
import io.github.teekas.hexlove.registry.HexloveBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Containers
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.phys.BlockHitResult
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class WorldSoulAltarBlock(properties: Properties) : BaseEntityBlock(properties) {
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(FACING, Direction.NORTH),
        )
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        WorldSoulAltarBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(ACTIVE, FACING)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult,
    ): InteractionResult {
        if (!level.isClientSide) {
            val altar = level.getBlockEntity(pos) as? WorldSoulAltarBlockEntity
            (player as? ServerPlayer)?.let { MenuRegistry.openMenu(it, altar ?: return@let) }
        }
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>,
    ): BlockEntityTicker<T>? = if (level.isClientSide) null else createTickerHelper(
        type,
        HexloveBlockEntities.WORLD_SOUL_ALTAR.value,
    ) { tickLevel, tickPos, tickState, altar ->
        if (tickLevel is ServerLevel) WorldSoulAltarBlockEntity.tick(tickLevel, tickPos, tickState, altar)
    }

    override fun animateTick(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        random: net.minecraft.util.RandomSource,
    ) {
        if (!state.getValue(ACTIVE) || random.nextInt(4) != 0) return
        val spoke = random.nextInt(4)
        val angle = spoke * (PI / 2.0) + (random.nextDouble() - 0.5) * 0.34
        val radius = 0.08 + random.nextDouble() * 0.32
        val colour = when (random.nextInt(10)) {
            0 -> WHITE
            in 1..3 -> VIOLET
            else -> PINK
        }
        level.addParticle(
            colour,
            pos.x + 0.5 + cos(angle) * radius,
            pos.y + 1.01 + random.nextDouble() * 0.16,
            pos.z + 0.5 + sin(angle) * radius,
            cos(angle) * (0.006 + random.nextDouble() * 0.008),
            0.018 + random.nextDouble() * 0.026,
            sin(angle) * (0.006 + random.nextDouble() * 0.008),
        )
        if (random.nextInt(12) == 0) {
            level.addParticle(
                WHITE,
                pos.x + 0.47 + random.nextDouble() * 0.06,
                pos.y + 1.08,
                pos.z + 0.47 + random.nextDouble() * 0.06,
                (random.nextDouble() - 0.5) * 0.008,
                0.045 + random.nextDouble() * 0.018,
                (random.nextDouble() - 0.5) * 0.008,
            )
        }
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block)) {
            (level.getBlockEntity(pos) as? WorldSoulAltarBlockEntity)?.let {
                Containers.dropContents(level, pos, it)
            }
        }
        super.onRemove(state, level, pos, newState, moved)
    }

    companion object {
        val ACTIVE: BooleanProperty = BooleanProperty.create("active")
        val FACING = HorizontalDirectionalBlock.FACING
        private val PINK = ConjureParticleOptions(0xFF78C8)
        private val VIOLET = ConjureParticleOptions(0xC997FF)
        private val WHITE = ConjureParticleOptions(0xFFF0FA)
    }
}
