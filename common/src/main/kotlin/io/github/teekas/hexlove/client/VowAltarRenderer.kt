package io.github.teekas.hexlove.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import io.github.teekas.hexlove.block.entity.VowAltarBlockEntity
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.item.ItemDisplayContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Renders the real offered stacks, so their personal/pair tint remains visible on the altar. */
class VowAltarRenderer(context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<VowAltarBlockEntity> {
    private val itemRenderer = context.itemRenderer

    override fun render(
        altar: VowAltarBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        val progress = altar.ritualProgress().toDouble()
        val ticks = (altar.level?.gameTime ?: 0L) + partialTick
        for (index in 0 until altar.containerSize) {
            val stack = altar.getItem(index)
            if (stack.isEmpty) continue
            val landing = altar.offeringArrivalProgress(index, ticks.toDouble())
            val landingEase = landing * (2.0 - landing)
            val approach = altar.offeringApproach(index)
            // In a top-down coordinate system this is the player's own left. Slot zero is the
            // left altar slot and slot one the right, no matter which side of the block they use.
            val leftX = -approach.z
            val leftZ = approach.x
            val side = if (index == 0) 1.0 else -1.0
            val angle = index * PI + if (progress > 0.0) ticks * 0.22 else 0.0
            val radius = if (progress > 0.0) 0.34 * (1.0 - progress * 0.86) else 0.26
            val targetX = if (progress > 0.0) 0.5 + cos(angle) * radius else 0.5 + leftX * side * radius
            // The altar is a full block: resting at y < 1 placed the item inside its model. Keep
            // it visibly above the top face even before the rite starts, with a quiet hover bob.
            val targetY = 1.28 + progress * 0.82 + sin(ticks * 0.14 + index * PI) * 0.045
            val targetZ = if (progress > 0.0) 0.5 + sin(angle) * radius else 0.5 + leftZ * side * radius
            val launchX = 0.5 + approach.x * 1.18 + leftX * side * 0.18
            val launchY = 1.18
            val launchZ = 0.5 + approach.z * 1.18 + leftZ * side * 0.18
            poseStack.pushPose()
            poseStack.translate(
                launchX + (targetX - launchX) * landingEase,
                launchY + (targetY - launchY) * landingEase + sin(landing * PI) * 0.48,
                launchZ + (targetZ - launchZ) * landingEase,
            )
            val scale = (0.42 + 0.06 * landing).toFloat()
            poseStack.scale(scale, scale, scale)
            poseStack.mulPose(Axis.XP.rotationDegrees(90f))
            val spin = ticks * (if (progress > 0.0) 10f else 1.5f) + index * 180f
            poseStack.mulPose(Axis.ZP.rotationDegrees(spin))
            itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT,
                packedOverlay,
                poseStack,
                buffer,
                altar.level,
                index,
            )
            poseStack.popPose()
        }
    }
}
