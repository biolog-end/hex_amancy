package io.github.teekas.hexlove.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import io.github.teekas.hexlove.block.WorldSoulAltarBlock
import io.github.teekas.hexlove.block.entity.WorldSoulAltarBlockEntity
import io.github.teekas.hexlove.registry.HexloveItems
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import kotlin.math.PI
import kotlin.math.sin

/** Turns both altar recipes into staged physical ceremonies above the authored offering sockets. */
class WorldSoulAltarRenderer(context: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<WorldSoulAltarBlockEntity> {
    private val itemRenderer = context.itemRenderer

    override fun render(
        altar: WorldSoulAltarBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        buffer: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        val ring = altar.getItem(WorldSoulAltarBlockEntity.RING_SLOT)
        val amethyst = altar.getItem(WorldSoulAltarBlockEntity.AMETHYST_SLOT)
        if (ring.isEmpty && amethyst.isEmpty) return
        val ticks = (altar.level?.gameTime ?: 0L) + partialTick
        val progress = altar.ritualProgress(partialTick)
        val ritual = altar.activeRitual()
        val active = altar.isStructureActive()
        val light = if (active) LightTexture.FULL_BRIGHT else packedLight
        val facingRotation = when (altar.blockState.getValue(WorldSoulAltarBlock.FACING)) {
            net.minecraft.core.Direction.NORTH -> 0f
            net.minecraft.core.Direction.EAST -> 90f
            net.minecraft.core.Direction.SOUTH -> 180f
            net.minecraft.core.Direction.WEST -> 270f
            else -> 0f
        }

        // Rotate the complete offering surface together with the authored front of the block.
        poseStack.pushPose()
        poseStack.translate(0.5, 0.0, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(facingRotation))
        poseStack.translate(-0.5, 0.0, -0.5)

        if (!amethyst.isEmpty) {
            val embodying = ritual == WorldSoulAltarBlockEntity.RitualKind.EMBODIMENT
            val airborne = if (embodying) airborneBlend(progress, EMBODIMENT_LIFT_END, EMBODIMENT_FALL_START) else 0.0
            val transformed = embodying && progress >= TRANSFORM_POINT
            val displayedStack = if (transformed) {
                ItemStack(HexloveItems.CRYSTALLIZED_AFFECTION.value, 1)
            } else {
                amethyst
            }
            val itemPose = altar.amethystRitualPose(progress.toDouble())
            val transformationPulse = if (transformed) {
                sin(((progress - TRANSFORM_POINT) / (1f - TRANSFORM_POINT)).coerceIn(0f, 1f) * PI).toFloat()
            } else {
                0f
            }
            poseStack.pushPose()
            poseStack.translate(
                itemPose.x,
                itemPose.y + sin(ticks * 0.17 + 1.4) * airborne * 0.032,
                itemPose.z,
            )
            val scale = lerp(0.34f, if (transformed) 0.49f else 0.42f, airborne.toFloat()) + transformationPulse * 0.055f
            poseStack.scale(scale, scale, scale)
            poseStack.mulPose(Axis.XP.rotationDegrees(lerp(90f, 22f, airborne.toFloat())))
            poseStack.mulPose(Axis.YP.rotationDegrees(ticks.toFloat() * if (embodying) 3.8f else 0f))
            poseStack.mulPose(Axis.ZP.rotationDegrees(45f + ticks.toFloat() * airborne.toFloat() * 2.5f))
            itemRenderer.renderStatic(
                displayedStack,
                ItemDisplayContext.FIXED,
                light,
                packedOverlay,
                poseStack,
                buffer,
                altar.level,
                1,
            )
            poseStack.popPose()
        }

        if (!ring.isEmpty) {
            val airborne = when (ritual) {
                WorldSoulAltarBlockEntity.RitualKind.ATTUNEMENT ->
                    airborneBlend(progress, ATTUNEMENT_LIFT_END, ATTUNEMENT_FALL_START)
                WorldSoulAltarBlockEntity.RitualKind.DETACHMENT ->
                    airborneBlend(progress, DETACHMENT_LIFT_END, DETACHMENT_FALL_START)
                WorldSoulAltarBlockEntity.RitualKind.EMBODIMENT ->
                    airborneBlend(progress, EMBODIMENT_LIFT_END, EMBODIMENT_FALL_START)
                WorldSoulAltarBlockEntity.RitualKind.NONE -> 0.0
            }
            val ringPose = altar.ringRitualPose(progress.toDouble())
            val ritualPulse = when (ritual) {
                WorldSoulAltarBlockEntity.RitualKind.ATTUNEMENT -> sin(ticks * 0.31) * airborne * 0.018
                WorldSoulAltarBlockEntity.RitualKind.DETACHMENT -> sin(ticks * 0.37) * airborne * 0.022
                WorldSoulAltarBlockEntity.RitualKind.EMBODIMENT -> sin(ticks * 0.43) * airborne * 0.014
                WorldSoulAltarBlockEntity.RitualKind.NONE -> 0.0
            }
            poseStack.pushPose()
            poseStack.translate(
                ringPose.x,
                ringPose.y + sin(ticks * 0.13) * if (airborne > 0.0) 0.032 else if (active) 0.025 else 0.008,
                ringPose.z,
            )
            val scale = (0.46 + ritualPulse).toFloat()
            poseStack.scale(scale, scale, scale)
            val hoverTilt = when (ritual) {
                WorldSoulAltarBlockEntity.RitualKind.ATTUNEMENT -> 12f
                WorldSoulAltarBlockEntity.RitualKind.DETACHMENT -> 154f
                else -> 26f
            }
            poseStack.mulPose(Axis.XP.rotationDegrees(lerp(90f, hoverTilt, airborne.toFloat())))
            val ySpin = when (ritual) {
                WorldSoulAltarBlockEntity.RitualKind.DETACHMENT -> -5.8f
                else -> 4.2f
            }
            poseStack.mulPose(Axis.YP.rotationDegrees(ticks.toFloat() * airborne.toFloat() * ySpin))
            poseStack.mulPose(
                Axis.ZP.rotationDegrees(
                    ticks.toFloat() * when (ritual) {
                        WorldSoulAltarBlockEntity.RitualKind.ATTUNEMENT -> if (airborne > 0.0) 9.5f else 1.15f
                        WorldSoulAltarBlockEntity.RitualKind.DETACHMENT -> if (airborne > 0.0) -8.2f else 1.15f
                        WorldSoulAltarBlockEntity.RitualKind.EMBODIMENT -> if (airborne > 0.0) 6.2f else 1.15f
                        WorldSoulAltarBlockEntity.RitualKind.NONE -> 1.15f
                    },
                ),
            )
            itemRenderer.renderStatic(
                ring,
                ItemDisplayContext.FIXED,
                light,
                packedOverlay,
                poseStack,
                buffer,
                altar.level,
                0,
            )
            poseStack.popPose()
        }

        poseStack.popPose()
    }

    private fun airborneBlend(progress: Float, liftEnd: Float, fallStart: Float): Double {
        val lift = smoothstep((progress / liftEnd).coerceIn(0f, 1f).toDouble())
        val fall = smoothstep(((progress - fallStart) / (1f - fallStart)).coerceIn(0f, 1f).toDouble())
        return lift * (1.0 - fall)
    }

    private fun smoothstep(value: Double): Double = value * value * (3.0 - 2.0 * value)

    private fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress

    private companion object {
        const val ATTUNEMENT_LIFT_END = 0.25f
        const val ATTUNEMENT_FALL_START = 0.90f
        const val DETACHMENT_LIFT_END = 0.22f
        const val DETACHMENT_FALL_START = 0.90f
        const val EMBODIMENT_LIFT_END = 0.28f
        const val EMBODIMENT_FALL_START = 0.90f
        const val TRANSFORM_POINT = 0.88f
    }
}
