package io.github.teekas.hexlove.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.ItemDisplay.class)
public interface MixinItemDisplayAccess {
    @Invoker("setItemTransform")
    void hexloveSetItemTransform(ItemDisplayContext context);
}
