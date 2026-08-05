package io.github.teekas.hexlove.client

import com.mojang.blaze3d.vertex.PoseStack
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.entity.Chimera
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer

/**
 * Renders the authored asymmetric chimera model. Physical size remains authoritative on the entity;
 * the extra baby factor mirrors the age-based collision shrink that a plain [ChimeraModel] does not
 * perform by itself.
 */
class ChimeraRenderer(context: EntityRendererProvider.Context) :
    MobRenderer<Chimera, ChimeraModel>(context, ChimeraModel(context.bakeLayer(ChimeraModel.LAYER_LOCATION)), 0.7f) {

    override fun getTextureLocation(entity: Chimera) = TEXTURE

    override fun scale(entity: Chimera, poseStack: PoseStack, partialTickTime: Float) {
        super.scale(entity, poseStack, partialTickTime)
        val scale = entity.sizeFactor * if (entity.isBaby) BABY_SCALE else 1.0f
        poseStack.scale(scale, scale, scale)
    }

    private companion object {
        const val BABY_SCALE = 0.5f
        val TEXTURE = Hexlove.id("textures/entity/chimera.png")
    }
}
