package io.github.teekas.hexlove.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.teekas.hexlove.nature.ChimeraNatures;

/**
 * Stage 3: when a serene player hits an Animal, the animal must not remember them as the
 * last hurt-by entity. This prevents retaliation and the animal's scare response.
 *
 * Injected at HEAD, cancellable. Cancel means setLastHurtByMob is a no-op for this call.
 * We check only: `this` is an Animal, the attacker is a serene Player.
 */
@Mixin(LivingEntity.class)
public abstract class MixinNatureLastHurtBy {

    @Inject(method = "setLastHurtByMob", at = @At("HEAD"), cancellable = true)
    private void hexlove$ignoreSereneAttacker(LivingEntity attacker, CallbackInfo ci) {
        try {
            if (!((Object) this instanceof Animal)) return;
            if (!(attacker instanceof Player player)) return;
            if (ChimeraNatures.INSTANCE.isSerene(player)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            // Never let an exception reach vanilla
        }
    }
}
