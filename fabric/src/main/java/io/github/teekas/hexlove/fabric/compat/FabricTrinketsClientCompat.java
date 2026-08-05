package io.github.teekas.hexlove.fabric.compat;

import dev.emi.trinkets.api.client.TrinketRendererRegistry;
import io.github.teekas.hexlove.client.RingHandPixel;
import io.github.teekas.hexlove.registry.HexloveItems;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/** Client-only renderer registration, isolated so Trinkets remains an optional dependency. */
public final class FabricTrinketsClientCompat {
    private FabricTrinketsClientCompat() {}

    public static void init() {
        TrinketRendererRegistry.registerRenderer(HexloveItems.INSTANCE.getSOULBOUND_RING().getValue(),
            (stack, slotReference, contextModel, poseStack, buffers, light, entity,
             limbAngle, limbDistance, partialTick, animationProgress, headYaw, headPitch) -> {
                var type = slotReference.inventory().getSlotType();
                if (!type.getName().equals("ring") ||
                    (!type.getGroup().equals("hand") && !type.getGroup().equals("offhand")) ||
                    !(entity instanceof Player player)) {
                    return;
                }
                HumanoidArm arm = type.getGroup().equals("hand")
                    ? player.getMainArm()
                    : opposite(player.getMainArm());
                RingHandPixel.render(stack, contextModel, poseStack, buffers, player, arm, partialTick);
            });
    }

    private static HumanoidArm opposite(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }
}
