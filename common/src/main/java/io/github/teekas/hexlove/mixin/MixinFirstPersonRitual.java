package io.github.teekas.hexlove.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.teekas.hexlove.item.CrystallizedAffectionItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person counterpart of the ritual pose; it deliberately renders both empty hands. */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinFirstPersonRitual {
    @Invoker("renderPlayerArm")
    protected abstract void hexloveRenderPlayerArm(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int light,
        float equippedProgress,
        float swingProgress,
        HumanoidArm arm
    );

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void hexloveRenderRitualArm(
        AbstractClientPlayer player,
        float partialTicks,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equippedProgress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int light,
        CallbackInfo ci
    ) {
        if (!player.isUsingItem()
            || !(player.getUseItem().getItem() instanceof CrystallizedAffectionItem)) {
            return;
        }

        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
            ? player.getMainArm()
            : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float phase = (player.getTicksUsingItem() + partialTicks) * 0.52F;
        if (arm != player.getMainArm()) phase += (float) Math.PI;

        poseStack.pushPose();
        poseStack.translate(
            side * (0.05F + (float) Math.cos(phase) * 0.08F),
            -0.08F + (float) Math.sin(phase) * 0.07F,
            -0.12F
        );
        poseStack.mulPose(Axis.XP.rotationDegrees(-8.0F + (float) Math.sin(phase) * 12.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * ((float) Math.cos(phase) * 16.0F)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * (12.0F + (float) Math.sin(phase) * 22.0F)));
        hexloveRenderPlayerArm(poseStack, buffer, light, equippedProgress, 0.0F, arm);
        poseStack.popPose();
        ci.cancel();
    }
}
