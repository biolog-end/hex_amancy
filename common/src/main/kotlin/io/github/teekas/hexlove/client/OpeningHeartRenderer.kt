package io.github.teekas.hexlove.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import io.github.teekas.hexlove.entity.OpeningHeartDisplay
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.DisplayRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.Display
import kotlin.math.PI

/** Vanilla's item-display renderer specialised for the short-lived ritual entity type. */
class OpeningHeartRenderer(context: EntityRendererProvider.Context) :
    DisplayRenderer<OpeningHeartDisplay, Display.ItemDisplay.ItemRenderState>(context) {
    private val itemRenderer: ItemRenderer = context.itemRenderer

    override fun getSubState(entity: OpeningHeartDisplay): Display.ItemDisplay.ItemRenderState =
        requireNotNull(entity.itemRenderState())

    override fun renderInner(
        entity: OpeningHeartDisplay,
        state: Display.ItemDisplay.ItemRenderState,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        partialTick: Float,
    ) {
        poseStack.mulPose(Axis.YP.rotation(PI.toFloat()))
        itemRenderer.renderStatic(
            state.itemStack(),
            state.itemTransform(),
            packedLight,
            OverlayTexture.NO_OVERLAY,
            poseStack,
            buffer,
            entity.level(),
            entity.id,
        )
    }
}
