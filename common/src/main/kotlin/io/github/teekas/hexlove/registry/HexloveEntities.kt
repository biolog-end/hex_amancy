package io.github.teekas.hexlove.registry

import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry
import dev.architectury.registry.client.level.entity.EntityRendererRegistry
import dev.architectury.registry.level.entity.EntityAttributeRegistry
import io.github.teekas.hexlove.client.ChimeraModel
import io.github.teekas.hexlove.client.ChimeraRenderer
import io.github.teekas.hexlove.client.OpeningHeartRenderer
import io.github.teekas.hexlove.client.PhantomIdealRenderer
import io.github.teekas.hexlove.entity.Chimera
import io.github.teekas.hexlove.entity.OpeningHeartDisplay
import io.github.teekas.hexlove.entity.PhantomIdeal
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object HexloveEntities : HexloveRegistrar<EntityType<*>>(
    Registries.ENTITY_TYPE,
    { BuiltInRegistries.ENTITY_TYPE },
) {
    val CHIMERA = register("chimera") {
        EntityType.Builder
            .of({ type, level -> Chimera(type, level) }, MobCategory.CREATURE)
            .sized(1.4f, 2.1f)
            .clientTrackingRange(10)
            .build("chimera")
    }

    val PHANTOM_IDEAL = register("phantom_ideal") {
        EntityType.Builder
            .of({ type, level -> PhantomIdeal(type, level) }, MobCategory.MISC)
            .sized(0.6f, 1.8f)
            .clientTrackingRange(64)
            .updateInterval(1)
            .build("phantom_ideal")
    }

    val OPENING_HEART = register("opening_heart") {
        EntityType.Builder
            .of({ type, level -> OpeningHeartDisplay(type, level) }, MobCategory.MISC)
            .sized(0.25f, 0.25f)
            .clientTrackingRange(32)
            .updateInterval(1)
            .build("opening_heart")
    }

    @Suppress("UNCHECKED_CAST")
    override fun init(registerer: (ResourceLocation, EntityType<*>) -> Unit) {
        super.init(registerer)
        // attributes cannot live in the type: both loaders want them from their own event
        EntityAttributeRegistry.register({ CHIMERA.value as EntityType<Chimera> }, Chimera::createAttributes)
        EntityAttributeRegistry.register({ PHANTOM_IDEAL.value as EntityType<PhantomIdeal> }, PhantomIdeal::createAttributes)
    }

    @Suppress("UNCHECKED_CAST")
    override fun initClient() {
        EntityModelLayerRegistry.register(ChimeraModel.LAYER_LOCATION, ChimeraModel::createBodyLayer)
        EntityRendererRegistry.register({ CHIMERA.value as EntityType<Chimera> }) { ChimeraRenderer(it) }
        EntityRendererRegistry.register({ PHANTOM_IDEAL.value as EntityType<PhantomIdeal> }) { PhantomIdealRenderer(it) }
        EntityRendererRegistry.register({ OPENING_HEART.value as EntityType<OpeningHeartDisplay> }) { OpeningHeartRenderer(it) }
    }
}
