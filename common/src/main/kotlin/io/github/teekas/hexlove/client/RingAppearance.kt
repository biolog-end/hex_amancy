package io.github.teekas.hexlove.client

import at.petrak.hexcasting.api.item.VariantItem
import at.petrak.hexcasting.xplat.IClientXplatAbstractions
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.marriage.RingData
import io.github.teekas.hexlove.registry.HexloveItems
import dev.architectury.registry.client.rendering.ColorHandlerRegistry
import dev.architectury.registry.item.ItemPropertiesRegistry
import net.minecraft.resources.ResourceLocation

/** Client-only model and tint hooks for the ring. There is deliberately no enchantment foil. */
object RingAppearance {
    fun init() {
        val ring = HexloveItems.SOULBOUND_RING.value
        ItemPropertiesRegistry.register(ring, Hexlove.id("active")) { stack, _, _, _ ->
            if (RingData.isBound(stack)) 1f else 0f
        }
        IClientXplatAbstractions.INSTANCE.registerItemProperty(
            ring,
            ResourceLocation("hexcasting", VariantItem.TAG_VARIANT),
        ) { stack, _, _, _ ->
            (stack.item as VariantItem).getVariant(stack).toFloat()
        }
        ColorHandlerRegistry.registerItemColors({ stack, tintIndex ->
            if (tintIndex in 1..4 && RingData.isBound(stack)) {
                RingData.animatedGradientColor(stack, tintIndex - 1, System.currentTimeMillis())
            } else {
                -1
            }
        }, ring)
    }
}
