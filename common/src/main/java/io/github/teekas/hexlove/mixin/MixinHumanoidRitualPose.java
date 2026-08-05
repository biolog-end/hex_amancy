package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.item.CrystallizedAffectionItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes both arms trace opposing magic circles in third person while the heart is opening. */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidRitualPose {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void hexloveRitualPose(
        LivingEntity entity,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch,
        CallbackInfo ci
    ) {
        if (!(entity instanceof Player player)
            || !player.isUsingItem()
            || !(player.getUseItem().getItem() instanceof CrystallizedAffectionItem)) {
            return;
        }

        float partialTick = Math.max(0.0F, Math.min(1.0F, ageInTicks - entity.tickCount));
        float phase = (player.getTicksUsingItem() + partialTick) * 0.52F;
        ModelPart main = player.getMainArm() == HumanoidArm.RIGHT ? rightArm : leftArm;
        ModelPart off = player.getMainArm() == HumanoidArm.RIGHT ? leftArm : rightArm;
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;

        main.xRot = -1.30F + (float) Math.cos(phase) * 0.28F;
        main.yRot = side * (-0.18F + (float) Math.sin(phase) * 0.48F);
        main.zRot = side * (0.22F + (float) Math.cos(phase) * 0.42F);

        float opposite = phase + (float) Math.PI;
        off.xRot = -1.12F + (float) Math.cos(opposite) * 0.34F;
        off.yRot = -side * (0.12F + (float) Math.sin(opposite) * 0.40F);
        off.zRot = -side * (0.30F + (float) Math.cos(opposite) * 0.34F);
    }
}
