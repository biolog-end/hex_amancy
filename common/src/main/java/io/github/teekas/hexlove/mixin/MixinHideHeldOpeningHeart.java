package io.github.teekas.hexlove.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.teekas.hexlove.item.CrystallizedAffectionItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides only the hand-held copy; the separate levitating item-display remains visible. */
@Mixin(ItemRenderer.class)
public abstract class MixinHideHeldOpeningHeart {
    @Inject(
        method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hexloveHideHeldHeart(
        LivingEntity entity,
        ItemStack stack,
        ItemDisplayContext context,
        boolean leftHand,
        PoseStack poseStack,
        MultiBufferSource buffer,
        Level level,
        int light,
        int overlay,
        int seed,
        CallbackInfo ci
    ) {
        if (entity != null
            && entity.isUsingItem()
            && stack.getItem() instanceof CrystallizedAffectionItem
            && (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)) {
            ci.cancel();
        }
    }
}
