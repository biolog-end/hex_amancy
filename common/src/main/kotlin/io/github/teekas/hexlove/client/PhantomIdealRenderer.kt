package io.github.teekas.hexlove.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import io.github.teekas.hexlove.entity.PhantomIdeal
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.resources.ResourceLocation

/**
 * A full-height rose silhouette. The standard adult-zombie layer supplies vanilla's two-block
 * humanoid proportions, while our translucent UV texture gives every blocky face a rose edge and
 * a pale, nearly transparent centre -- never a recoloured Steve skin.
 */
class PhantomIdealRenderer(context: EntityRendererProvider.Context) :
    MobRenderer<PhantomIdeal, PhantomIdealModel>(
        context,
        PhantomIdealModel(context.bakeLayer(ModelLayers.ZOMBIE)),
        0f,
    ) {

    override fun getTextureLocation(entity: PhantomIdeal): ResourceLocation = TEXTURE

    override fun getRenderType(
        entity: PhantomIdeal,
        bodyVisible: Boolean,
        translucent: Boolean,
        glowing: Boolean,
    ): RenderType = RenderType.entityTranslucentEmissive(TEXTURE)

    private companion object {
        val TEXTURE = ResourceLocation("hexlove", "textures/entity/phantom_ideal.png")
    }
}

class PhantomIdealModel(root: ModelPart) : HumanoidModel<PhantomIdeal>(root) {
    override fun renderToBuffer(
        poseStack: PoseStack,
        vertexConsumer: VertexConsumer,
        packedLight: Int,
        packedOverlay: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        super.renderToBuffer(
            poseStack,
            vertexConsumer,
            LightTexture.FULL_BRIGHT,
            0,
            1f,
            1f,
            1f,
            0.46f,
        )
    }
}
