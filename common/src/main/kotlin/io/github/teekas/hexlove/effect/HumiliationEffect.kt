package io.github.teekas.hexlove.effect

import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes

/** Penalty state applied when a serene player is cornered by an animal they tried to befriend. */
class HumiliationEffect : MobEffect(MobEffectCategory.HARMFUL, 0xF2ECEC) {
    init {
        addAttributeModifier(
            Attributes.ATTACK_DAMAGE,
            "d2a3c4e5-7f1b-4d9a-8c2e-5b0f3e1a6d8c",
            -0.50,
            AttributeModifier.Operation.MULTIPLY_TOTAL,
        )
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            "e4b5d6f7-8a2c-4e1b-9d3f-6c0e2a4b7f1d",
            -0.30,
            AttributeModifier.Operation.MULTIPLY_TOTAL,
        )
    }
}
