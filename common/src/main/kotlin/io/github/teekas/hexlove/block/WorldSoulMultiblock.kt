package io.github.teekas.hexlove.block

import io.github.teekas.hexlove.registry.HexloveBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks

/**
 * A compact 7x7 ritual court: a 5x5 amethyst dais, four flower sigils, and four
 * independently-sized pillars. Pillars may be two, three, or four blocks tall.
 */
object WorldSoulMultiblock {
    data class Result(val candles: List<BlockPos>)
    data class Inspection(val result: Result?, val problem: Problem)

    enum class Problem(val code: Int) {
        NONE(0),
        DAIS(1),
        PILLAR_FOUNDATIONS(2),
        FLOWERS(3),
        PILLARS_OR_CANDLES(4),
    }

    fun inspect(level: ServerLevel, altar: BlockPos): Inspection {
        // The central dais is solid so a visualiser never hides a hollow exploit underneath it.
        for (x in -2..2) {
            for (z in -2..2) {
                if (!level.getBlockState(altar.offset(x, -1, z)).`is`(Blocks.AMETHYST_BLOCK)) {
                    return Inspection(null, Problem.DAIS)
                }
            }
        }

        // Each pillar has its own small foundation beyond the dais.
        for ((x, z) in PILLARS) {
            if (!level.getBlockState(altar.offset(x, -1, z)).`is`(Blocks.AMETHYST_BLOCK)) {
                return Inspection(null, Problem.PILLAR_FOUNDATIONS)
            }
        }

        // Patchouli lets the player rotate and mirror the displayed court. Requiring a rose at
        // literal world-north made those equally valid copies look correct but fail validation.
        // Every cardinal mark still needs a flower, and the set must contain all three varieties;
        // only their orientation (and which variety is repeated) is intentionally unrestricted.
        if (!hasCompleteFlowerSigil(level, altar)) return Inspection(null, Problem.FLOWERS)

        val candles = ArrayList<BlockPos>(4)
        for ((x, z) in PILLARS) {
            val candle = findPillarCandle(level, altar, x, z)
                ?: return Inspection(null, Problem.PILLARS_OR_CANDLES)
            candles += candle
        }
        return Inspection(Result(candles), Problem.NONE)
    }

    fun validate(level: ServerLevel, altar: BlockPos): Result? = inspect(level, altar).result

    private fun findPillarCandle(level: ServerLevel, altar: BlockPos, x: Int, z: Int): BlockPos? {
        for (height in MIN_PILLAR_HEIGHT..MAX_PILLAR_HEIGHT) {
            var solid = true
            for (y in 0 until height) {
                if (!level.getBlockState(altar.offset(x, y, z)).`is`(Blocks.AMETHYST_BLOCK)) {
                    solid = false
                    break
                }
            }
            if (!solid) continue
            val candlePos = altar.offset(x, height, z)
            val state = level.getBlockState(candlePos)
            if (state.`is`(HexloveBlocks.CANDLES_OF_INFATUATION.value) &&
                state.getValue(InfatuationCandleBlock.ATTACHMENT) == Direction.UP
            ) {
                return candlePos.immutable()
            }
        }
        return null
    }

    private fun hasCompleteFlowerSigil(level: ServerLevel, altar: BlockPos): Boolean {
        var hasRose = false
        var hasDandelion = false
        var hasTulip = false
        for ((x, z) in FLOWER_MARKS) {
            val state = level.getBlockState(altar.offset(x, 0, z))
            when {
                state.`is`(HexloveBlocks.AMETHYST_ROSE.value) -> hasRose = true
                state.`is`(HexloveBlocks.AMETHYST_DANDELION.value) -> hasDandelion = true
                state.`is`(HexloveBlocks.AMETHYST_TULIP.value) -> hasTulip = true
                else -> return false
            }
        }
        return hasRose && hasDandelion && hasTulip
    }

    private const val MIN_PILLAR_HEIGHT = 2
    private const val MAX_PILLAR_HEIGHT = 4
    private val FLOWER_MARKS = arrayOf(0 to -2, 2 to 0, 0 to 2, -2 to 0)
    private val PILLARS = arrayOf(-3 to -3, 3 to -3, 3 to 3, -3 to 3)
}
