package io.github.teekas.hexlove

import io.github.teekas.hexlove.client.AmethystHeartScreen
import io.github.teekas.hexlove.client.HexloveHud
import io.github.teekas.hexlove.client.RingAppearance
import io.github.teekas.hexlove.client.VowAltarRenderer
import io.github.teekas.hexlove.client.VowAltarScreen
import io.github.teekas.hexlove.client.WorldSoulAltarRenderer
import io.github.teekas.hexlove.client.WorldSoulAltarScreen
import io.github.teekas.hexlove.config.HexloveClientConfig
import io.github.teekas.hexlove.registry.HexloveBlockEntities
import io.github.teekas.hexlove.registry.HexloveBlocks
import io.github.teekas.hexlove.registry.HexloveMenus
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry
import dev.architectury.registry.client.rendering.RenderTypeRegistry
import dev.architectury.registry.menu.MenuRegistry
import me.shedaniel.autoconfig.AutoConfig
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderType

object HexloveClient {
    fun init() {
        HexloveClientConfig.init()
        HexloveHud.init()
        RingAppearance.init()
        // The flowers and their potted variants contain transparent pixels. Without the cutout
        // layer those pixels are written as black by the solid terrain renderer.
        RenderTypeRegistry.register(
            RenderType.cutout(),
            HexloveBlocks.AMETHYST_ROSE.value,
            HexloveBlocks.AMETHYST_DANDELION.value,
            HexloveBlocks.AMETHYST_TULIP.value,
            HexloveBlocks.POTTED_AMETHYST_ROSE.value,
            HexloveBlocks.POTTED_AMETHYST_DANDELION.value,
            HexloveBlocks.POTTED_AMETHYST_TULIP.value,
        )
        BlockEntityRendererRegistry.register(HexloveBlockEntities.VOW_ALTAR.value, ::VowAltarRenderer)
        BlockEntityRendererRegistry.register(HexloveBlockEntities.WORLD_SOUL_ALTAR.value, ::WorldSoulAltarRenderer)
        MenuRegistry.registerScreenFactory(HexloveMenus.VOW_ALTAR.value) { menu, inventory, title ->
            VowAltarScreen(menu, inventory, title)
        }
        MenuRegistry.registerScreenFactory(HexloveMenus.AMETHYST_HEART.value) { menu, inventory, title ->
            AmethystHeartScreen(menu, inventory, title)
        }
        MenuRegistry.registerScreenFactory(HexloveMenus.WORLD_SOUL_ALTAR.value) { menu, inventory, title ->
            WorldSoulAltarScreen(menu, inventory, title)
        }
    }

    fun getConfigScreen(parent: Screen): Screen {
        return AutoConfig.getConfigScreen(HexloveClientConfig.GlobalConfig::class.java, parent).get()
    }
}
