package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.ChimeraNatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Stage 4: weak night vision for ferocious players.
 *
 * {@code @Redirect} on the {@code OptionInstance.get()} call that reads the gamma option inside
 * {@code LightTexture.updateLightTexture(float)}. When the local player is ferocious, the returned
 * gamma is raised to at least 0.62 (roughly equivalent to "night vision but dim"), without the
 * flickering that a short vanilla NIGHT_VISION effect causes.
 *
 * Declared with {@code require = 0}: if a mapping mismatch causes the redirect to fail to apply,
 * the mixin simply skips it — no crash, no night vision. Every other mixin in this project keeps
 * {@code defaultRequire: 1}; this is the sole intentional exception as documented in the spec.
 *
 * Verified method shape in Minecraft 1.20.1: the gamma option is read via
 * {@code this.minecraft.options.gamma().get()} inside {@code updateLightTexture(float)}, where
 * {@code gamma()} returns {@code OptionInstance<Double>} and {@code get()} returns {@code Object}
 * after type erasure.
 */
@Mixin(LightTexture.class)
public abstract class MixinFerociousNightVision {

    /**
     * Replace the gamma option read in updateLightTexture with max(real, 0.62) for ferocious players.
     *
     * The target is the single {@code OptionInstance.get()} call in this method. There is exactly
     * one such call in 1.20.1; {@code require = 0} guards against mapping changes.
     */
    @Redirect(
        method = "updateLightTexture",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"
        ),
        require = 0
    )
    private Object hexlove$boostGammaForFerocious(OptionInstance<?> instance) {
        Object value = instance.get();
        try {
            var localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null && ChimeraNatures.INSTANCE.isFerocious(localPlayer)) {
                if (value instanceof Double d) {
                    return Math.max(d, 0.62);
                }
            }
        } catch (Throwable t) {
            // require=0 means a mapping failure skips this entirely; a runtime failure
            // inside must also be silent to match the same soft-fail contract.
        }
        return value;
    }
}
