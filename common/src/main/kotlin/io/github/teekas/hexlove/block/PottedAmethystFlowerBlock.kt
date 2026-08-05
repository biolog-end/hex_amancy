package io.github.teekas.hexlove.block

import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.FlowerPotBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState

/** A potted flower keeps a smaller, gentler version of its wild bloom's magic. */
class PottedAmethystFlowerBlock(
    flower: Block,
    moteColour: Int,
    properties: BlockBehaviour.Properties,
) : FlowerPotBlock(flower, properties) {
    private val mote = ConjureParticleOptions(moteColour)

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        if (random.nextInt(4) != 0) return
        level.addParticle(
            mote,
            pos.x + 0.36 + random.nextDouble() * 0.28,
            pos.y + 0.44 + random.nextDouble() * 0.34,
            pos.z + 0.36 + random.nextDouble() * 0.28,
            (random.nextDouble() - 0.5) * 0.008,
            0.009 + random.nextDouble() * 0.012,
            (random.nextDouble() - 0.5) * 0.008,
        )
    }
}
