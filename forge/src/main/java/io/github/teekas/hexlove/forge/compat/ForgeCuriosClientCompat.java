package io.github.teekas.hexlove.forge.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.teekas.hexlove.client.RingHandPixel;
import io.github.teekas.hexlove.registry.HexloveItems;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Client-only renderer registration, isolated so Curios remains an optional dependency. */
public final class ForgeCuriosClientCompat {
    private ForgeCuriosClientCompat() {}

    public static void init() {
        CuriosRendererRegistry.register(HexloveItems.INSTANCE.getSOULBOUND_RING().getValue(), RingRenderer::new);
    }

    private static final class RingRenderer implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack,
            SlotContext slotContext,
            PoseStack poseStack,
            RenderLayerParent<T, M> renderLayerParent,
            MultiBufferSource buffers,
            int light,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
        ) {
            if (!slotContext.identifier().equals("ring") || slotContext.cosmetic() ||
                !(slotContext.entity() instanceof Player player)) {
                return;
            }
            RingHandPixel.render(
                stack,
                renderLayerParent.getModel(),
                poseStack,
                buffers,
                player,
                player.getMainArm(),
                partialTick
            );
        }
    }
}
