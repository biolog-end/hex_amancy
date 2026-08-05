package io.github.teekas.hexlove.block

import at.petrak.hexcasting.api.misc.MediaConstants
import io.github.teekas.hexlove.block.entity.AmethystHeartBlockEntity
import io.github.teekas.hexlove.fx.BondFx
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.phys.Vec3

/**
 * Finds loaded hearts near a completed birth. Breeding is rare, so a small local block scan is
 * exact and cheap. Reservoir sampling chooses one heart uniformly without retaining a collection:
 * one birth can never be multiplied merely by surrounding the parents with more lamps.
 */
object AmethystHeartManager {
    private const val RANGE = 12
    private const val RANGE_SQR = RANGE.toDouble() * RANGE
    /** The full yield of one birth: a random 1/7 through 1/2 of a dust, chosen once per event. */
    private const val MIN_MEDIA_PER_BREEDING = MediaConstants.DUST_UNIT / 7L
    private const val MAX_MEDIA_PER_BREEDING = MediaConstants.DUST_UNIT / 2L

    @JvmStatic
    fun onAnimalBred(level: ServerLevel, first: Animal, second: Animal) {
        if (!first.isAlive || !second.isAlive) return
        val media = mediaForBreeding(level.random)
        val centre = first.position().add(second.position()).scale(0.5)
        val min = BlockPos.containing(centre.x - RANGE, centre.y - RANGE, centre.z - RANGE)
        val max = BlockPos.containing(centre.x + RANGE, centre.y + RANGE, centre.z + RANGE)
        var eligibleHearts = 0
        var chosenPos: BlockPos? = null
        var chosenHeart: AmethystHeartBlockEntity? = null
        for (pos in BlockPos.betweenClosed(min, max)) {
            if (Vec3.atCenterOf(pos).distanceToSqr(centre) > RANGE_SQR) continue
            val heart = level.getBlockEntity(pos) as? AmethystHeartBlockEntity ?: continue
            eligibleHearts++
            if (level.random.nextInt(eligibleHearts) == 0) {
                // betweenClosed reuses a mutable cursor; keep a stable position for the FX call.
                chosenPos = pos.immutable()
                chosenHeart = heart
            }
        }

        val heart = chosenHeart ?: return
        val pos = chosenPos ?: return
        val accepted = heart.receive(media)
        if (accepted < media) {
            BondFx.loveOverflow(level, pos, first, second)
        } else {
            BondFx.loveHarvest(level, first, second, pos)
        }
    }

    private fun mediaForBreeding(random: RandomSource): Long =
        MIN_MEDIA_PER_BREEDING + random.nextInt((MAX_MEDIA_PER_BREEDING - MIN_MEDIA_PER_BREEDING + 1L).toInt())
}
