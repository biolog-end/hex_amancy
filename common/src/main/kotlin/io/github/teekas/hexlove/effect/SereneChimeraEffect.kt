package io.github.teekas.hexlove.effect

import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes

/** Herbivore nature: slows melee output in exchange for passive harmony with animals. */
class SereneChimeraEffect : MobEffect(MobEffectCategory.NEUTRAL, 0x8FD46A) {
    init {
        addAttributeModifier(
            Attributes.ATTACK_DAMAGE,
            "a3c1e2f0-4b7d-4e8a-9f2c-1d0e5b3a7c9f",
            -0.30,
            AttributeModifier.Operation.MULTIPLY_TOTAL,
        )
    }
}
