package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.ChimeraNatures;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stage 4: a ferocious player has undead anatomy — Potion of Healing harms them and Potion of
 * Harming heals them.
 *
 * Vanilla already performs this swap whenever {@code isInvertedHealAndHarm()} returns {@code true}.
 * We do not implement the swap ourselves; we only flip the flag. Only Players are checked —
 * non-player entities are unaffected by chimera nature.
 */
@Mixin(LivingEntity.class)
public abstract class MixinNatureInvertedHeal {

    @Inject(method = "isInvertedHealAndHarm", at = @At("RETURN"), cancellable = true)
    private void hexlove$ferociousInvertedHeal(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (Boolean.TRUE.equals(cir.getReturnValue())) return; // already true, no need to touch
            if (!((Object) this instanceof Player player)) return;
            if (ChimeraNatures.INSTANCE.isFerocious(player)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            // Never let an exception reach vanilla
        }
    }
}
