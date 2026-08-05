package io.github.teekas.hexlove.item

import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.nature.ChimeraNature
import io.github.teekas.hexlove.nature.ChimeraNatures
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BowlFoodItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Consumes like a mushroom stew (returns the bowl), then grants the appropriate chimera nature
 * for the configured duration. Duration is read from the server config so operators can tune it
 * without a code change.
 */
class NatureStewItem(private val nature: ChimeraNature, properties: Properties) : BowlFoodItem(properties) {
    override fun finishUsingItem(stack: ItemStack, level: Level, consumer: LivingEntity): ItemStack {
        // Let BowlFoodItem credit vanilla nutrition and hand back the bowl first.
        val bowl = super.finishUsingItem(stack, level, consumer)

        if (!level.isClientSide && consumer is Player && level is ServerLevel) {
            val cfg = HexloveServerConfig.config.nature
            val ticks = when (nature) {
                ChimeraNature.SERENE -> cfg.serenityDurationTicks
                ChimeraNature.FEROCIOUS -> cfg.ferocityDurationTicks
            }
            ChimeraNatures.apply(consumer, nature, ticks)
            burst(level, consumer)
        }

        return bowl
    }

    /**
     * A burst of Hex Casting conjure particles around the eater the moment the stew lands. Both
     * natures share the same shape; only the palette differs so the player instantly gets a hint
     * which way their body just turned.
     */
    private fun burst(level: ServerLevel, player: Player) {
        val (a, b) = when (nature) {
            ChimeraNature.SERENE    -> 0x8FD46A to 0xD9F7B0            // mossy greens
            ChimeraNature.FEROCIOUS -> 0xC93A2C to 0xFFB84D            // ember red-orange
        }
        val cx = player.x; val cy = player.y + 1.0; val cz = player.z
        level.sendParticles(ConjureParticleOptions(a), cx, cy, cz, 40, 0.5, 0.6, 0.5, 0.06)
        level.sendParticles(ConjureParticleOptions(b), cx, cy, cz, 30, 0.4, 0.5, 0.4, 0.05)
        // Vanilla hearts around the eater — the same feedback fed animals give — so the player
        // reads the moment as an act of tenderness/violence rather than a passive food buff.
        level.sendParticles(ParticleTypes.HEART, cx, cy + 0.3, cz, 8, 0.6, 0.4, 0.6, 0.0)
    }
}
