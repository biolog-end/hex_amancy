package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.ChimeraNatures;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stage 4: a ferocious (ghoul) player cannot receive Poison, Hunger, or Nausea (Confusion).
 *
 * This also closes "immune to food poisoning": rotten flesh and raw chicken apply Hunger and
 * Poison respectively, and both calls go through canBeAffected before the effect is added.
 * Nausea immunity keeps rotten flesh / pufferfish / phantom bites from disorienting a ghoul.
 *
 * Only Players are checked — non-player entities are unaffected by chimera nature.
 */
@Mixin(LivingEntity.class)
public abstract class MixinNatureEffectImmunity {

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void hexlove$ferociousEffectImmunity(MobEffectInstance effectInstance, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!((Object) this instanceof Player player)) return;
            if (!ChimeraNatures.INSTANCE.isFerocious(player)) return;
            MobEffect effect = effectInstance.getEffect();
            if (effect == MobEffects.POISON || effect == MobEffects.HUNGER || effect == MobEffects.CONFUSION) {
                cir.setReturnValue(false);
            }
        } catch (Throwable t) {
            // Never let an exception reach vanilla
        }
    }
}
