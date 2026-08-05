package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.bond.BondGate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Requirements 4.5, 8.6: a charmed mob can never pick the owner of its own Love_Bond as a target.
 * Vetoing here instead of removing vanilla goals keeps the rest of the mob's AI intact.
 */
@Mixin(Mob.class)
public abstract class MixinMobSetTarget {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void hexlove$vetoTarget(LivingEntity target, CallbackInfo ci) {
        if (target == null) {
            return;
        }
        if (BondGate.forbidsTargeting((Mob) (Object) this, target)) {
            ci.cancel();
        }
    }
}
