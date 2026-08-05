package io.github.teekas.hexlove.effect

import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes

/** Ghoul nature: bonus damage and speed, countered by fast hunger and social exile. */
class FerociousChimeraEffect : MobEffect(MobEffectCategory.NEUTRAL, 0x8E2B2B) {
    init {
        addAttributeModifier(
            Attributes.ATTACK_DAMAGE,
            "b7f4a1d3-5c2e-4f9b-8e1a-2d3f6c0b4e8a",
            0.20,
            AttributeModifier.Operation.MULTIPLY_TOTAL,
        )
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            "c9e2b5f1-6d3a-4c8e-9b2f-4e1d7a3c5b0f",
            0.20,
            AttributeModifier.Operation.MULTIPLY_TOTAL,
        )
    }
}
