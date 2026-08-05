package io.github.teekas.hexlove.effect

import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes

/** Speed and luck live on the effect; hostile-only damage is applied at the damage boundary. */
class WorldsEmbraceEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0xD67CFF) {
    init {
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            "6820f774-4f8a-47d7-bf3f-b142005f5a71",
            0.10,
            AttributeModifier.Operation.MULTIPLY_TOTAL,
        )
        addAttributeModifier(
            Attributes.LUCK,
            "41631fd4-8a73-4dde-bd4e-2da28a241943",
            1.0,
            AttributeModifier.Operation.ADDITION,
        )
    }
}
