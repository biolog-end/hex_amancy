package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.client.NatureHudIcons;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces the vanilla food-strip texture with a nature-specific atlas — mossy green under
 * serenity, ember red-orange under ferocity. In 1.20.1 the food and hearts are drawn from
 * one method ({@code renderPlayerHealth}), so we intercept every {@code blit(...)} call
 * inside it and swap the atlas only when the vertical UV lands in the drumstick row
 * (27..35). Every other icon — hearts, armour, air, xp — keeps drawing from vanilla
 * icons.png.
 */
@Mixin(Gui.class)
public abstract class MixinNatureHungerIcons {

    @Redirect(
        method = "renderPlayerHealth",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"
        ),
        require = 0
    )
    private void hexlove$rebindFoodAtlas(
        GuiGraphics graphics,
        ResourceLocation vanilla,
        int x, int y, int u, int v, int width, int height
    ) {
        ResourceLocation atlas = vanilla;
        if (v >= 27 && v <= 35) {
            atlas = NatureHudIcons.currentFoodAtlas(vanilla);
        }
        graphics.blit(atlas, x, y, u, v, width, height);
    }
}
