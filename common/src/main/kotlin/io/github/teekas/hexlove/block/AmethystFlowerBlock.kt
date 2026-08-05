package io.github.teekas.hexlove.block

import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import net.minecraft.core.BlockPos
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FlowerBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState

/**
 * A normal small flower that has grown a tiny amethyst lattice through its petals. It deliberately
 * keeps vanilla flower survival rules, with amethyst added as a second kind of soil, so it works
 * in every place a poppy or tulip would normally grow.
 */
class AmethystFlowerBlock(
    private val moteColour: Int,
    properties: BlockBehaviour.Properties,
) : FlowerBlock(MobEffects.LUCK, 5, properties) {
    private val mote = ConjureParticleOptions(moteColour)

    override fun mayPlaceOn(state: BlockState, level: BlockGetter, pos: BlockPos): Boolean =
        super.mayPlaceOn(state, level, pos) || state.`is`(Blocks.AMETHYST_BLOCK) || state.`is`(Blocks.BUDDING_AMETHYST)

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: net.minecraft.util.RandomSource) {
        super.animateTick(state, level, pos, random)
        if (random.nextInt(5) != 0) return
        level.addParticle(
            mote,
            pos.x + 0.3 + random.nextDouble() * 0.4,
            pos.y + 0.28 + random.nextDouble() * 0.35,
            pos.z + 0.3 + random.nextDouble() * 0.4,
            (random.nextDouble() - 0.5) * 0.012,
            0.012 + random.nextDouble() * 0.018,
            (random.nextDouble() - 0.5) * 0.012,
        )
    }
}
