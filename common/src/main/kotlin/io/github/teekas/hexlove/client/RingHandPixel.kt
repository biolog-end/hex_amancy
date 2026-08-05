package io.github.teekas.hexlove.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.teekas.hexlove.marriage.RingData
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f

/** Draws one model-pixel of the equipped ring's living gradient on the wearer's wrist. */
object RingHandPixel {
    @JvmStatic
    fun render(
        stack: ItemStack,
        contextModel: EntityModel<*>,
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        wearer: LivingEntity,
        arm: HumanoidArm,
        partialTick: Float,
    ) {
        if (!RingData.isBound(stack)) return
        val humanoid = contextModel as? HumanoidModel<*> ?: return
        val armPart = if (arm == HumanoidArm.RIGHT) humanoid.rightArm else humanoid.leftArm

        poseStack.pushPose()
        armPart.translateAndRotate(poseStack)

        val timeMillis = ((wearer.tickCount.toDouble() + partialTick) * MILLIS_PER_TICK).toLong()
        val first = RingData.animatedGradientColor(stack, 0, timeMillis)
        val second = RingData.animatedGradientColor(stack, 1, timeMillis)
        val consumer = buffers.getBuffer(RenderType.lightning())
        val pose = poseStack.last().pose()

        val xCentre = if (arm == HumanoidArm.RIGHT) -ONE_PIXEL else ONE_PIXEL
        val x0 = xCentre - HALF_PIXEL
        val x1 = xCentre + HALF_PIXEL
        val y0 = WRIST_TOP
        val y1 = WRIST_TOP + ONE_PIXEL
        // The vanilla sleeve projects a quarter pixel from the arm. This shallow gem sits just
        // beyond it, so it remains visible without looking like a full bracelet.
        val z0 = ARM_FRONT - GEM_PROTRUSION
        val z1 = ARM_FRONT + GEM_DEPTH

        quad(consumer, pose, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, first, second)
        quad(consumer, pose, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, second, first)
        quad(consumer, pose, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, first, second)
        quad(consumer, pose, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, second, first)
        quad(consumer, pose, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, first, second)
        quad(consumer, pose, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, second, first)

        poseStack.popPose()
    }

    private fun quad(
        consumer: VertexConsumer,
        pose: Matrix4f,
        ax: Float,
        ay: Float,
        az: Float,
        bx: Float,
        by: Float,
        bz: Float,
        cx: Float,
        cy: Float,
        cz: Float,
        dx: Float,
        dy: Float,
        dz: Float,
        first: Int,
        second: Int,
    ) {
        vertex(consumer, pose, ax, ay, az, first)
        vertex(consumer, pose, bx, by, bz, second)
        vertex(consumer, pose, cx, cy, cz, second)
        vertex(consumer, pose, dx, dy, dz, first)
    }

    private fun vertex(consumer: VertexConsumer, pose: Matrix4f, x: Float, y: Float, z: Float, colour: Int) {
        consumer.vertex(pose, x, y, z)
            .color(colour shr 16 and 0xff, colour shr 8 and 0xff, colour and 0xff, ALPHA)
            .endVertex()
    }

    private const val ONE_PIXEL = 1.0f / 16.0f
    private const val HALF_PIXEL = ONE_PIXEL / 2.0f
    private const val WRIST_TOP = 7.25f / 16.0f
    private const val ARM_FRONT = -2.0f / 16.0f
    private const val GEM_PROTRUSION = 0.55f / 16.0f
    private const val GEM_DEPTH = 0.05f / 16.0f
    private const val MILLIS_PER_TICK = 50.0
    private const val ALPHA = 224
}
