package io.github.teekas.hexlove.client

import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.nature.ChimeraNature
import io.github.teekas.hexlove.nature.ChimeraNatures
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

/**
 * Which drumstick atlas to blit for the local player at any given moment. Kept in a tiny
 * client-only helper so [io.github.teekas.hexlove.mixin.MixinNatureHungerIcons] stays
 * absolutely trivial. This runs inside `Gui.renderFood`, once per hunger icon draw call —
 * so it must return fast and must never throw.
 */
@Environment(EnvType.CLIENT)
object NatureHudIcons {

    /** Static green-leaf food strip for herbivore mode. */
    private val HERBIVORE = ResourceLocation(Hexlove.MODID, "textures/gui/hunger_herbivore.png")

    /**
     * Ferocious mode has a three-frame ember pulse. Since Minecraft's SimpleTexture path
     * doesn't animate .mcmeta on GUI blits, we swap the bound atlas ourselves — one frame
     * every [FEROCIOUS_FRAME_MS] milliseconds.
     */
    private val FEROCIOUS_FRAMES = arrayOf(
        ResourceLocation(Hexlove.MODID, "textures/gui/hunger_ferocious_0.png"),
        ResourceLocation(Hexlove.MODID, "textures/gui/hunger_ferocious_1.png"),
        ResourceLocation(Hexlove.MODID, "textures/gui/hunger_ferocious_2.png"),
    )
    private const val FEROCIOUS_FRAME_MS = 220L

    /**
     * Returns the atlas the food strip should sample from right now, given the [vanilla]
     * location the game was originally about to bind. If no nature is active, or anything
     * goes wrong, we fall back to [vanilla] — so vanilla food icons render as usual.
     */
    @JvmStatic
    fun currentFoodAtlas(vanilla: ResourceLocation): ResourceLocation = try {
        val player = Minecraft.getInstance().player
        if (player == null) vanilla
        else when (ChimeraNatures.of(player)) {
            ChimeraNature.SERENE -> HERBIVORE
            ChimeraNature.FEROCIOUS -> {
                val idx = ((System.currentTimeMillis() / FEROCIOUS_FRAME_MS) % FEROCIOUS_FRAMES.size.toLong()).toInt()
                FEROCIOUS_FRAMES[idx]
            }
            null -> vanilla
        }
    } catch (_: Throwable) {
        vanilla
    }
}
