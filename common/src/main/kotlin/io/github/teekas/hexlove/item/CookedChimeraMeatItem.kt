package io.github.teekas.hexlove.item

import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Chimera flesh is nourishing once cooked, but its mixed nature leaves a brief, unpredictable
 * trace in whoever eats it. Every serving selects exactly one listed effect for five to thirty
 * seconds, so it remains a useful meal rather than a reliable potion source.
 */
class CookedChimeraMeatItem(properties: Properties) : Item(properties) {
    override fun finishUsingItem(stack: ItemStack, level: Level, consumer: LivingEntity): ItemStack {
        val finished = super.finishUsingItem(stack, level, consumer)
        if (!level.isClientSide) {
            val effect = EFFECTS[level.random.nextInt(EFFECTS.size)]
            val duration = MIN_DURATION_TICKS + level.random.nextInt(MAX_DURATION_TICKS - MIN_DURATION_TICKS + 1)
            consumer.addEffect(MobEffectInstance(effect, duration))
            (consumer as? ServerPlayer)?.let {
                HexloveAdvancements.grant(it, HexloveAdvancements.BIZARRE_GOURMAND)
            }
        }
        return finished
    }

    private companion object {
        val EFFECTS = arrayOf(
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.WEAKNESS,
            MobEffects.JUMP,
            MobEffects.NIGHT_VISION,
            MobEffects.FIRE_RESISTANCE,
            MobEffects.DAMAGE_BOOST,
            MobEffects.REGENERATION,
            MobEffects.SLOW_FALLING,
            MobEffects.LUCK,
            MobEffects.MOVEMENT_SPEED,
            MobEffects.INVISIBILITY,
        )
        const val MIN_DURATION_TICKS = 20 * 5
        const val MAX_DURATION_TICKS = 20 * 30
    }
}
