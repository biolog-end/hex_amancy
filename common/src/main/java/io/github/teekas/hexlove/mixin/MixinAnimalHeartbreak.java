package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.bond.BondGate;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Requirement 18, revised: Heartbreak was only ever checked by this mod's own actions, so a
 * heartbroken cow still bred happily from a handful of wheat and the state was, in play, invisible.
 * A broken heart now refuses vanilla courtship too, which is what makes it an actual effect.
 */
@Mixin(Animal.class)
public abstract class MixinAnimalHeartbreak {
    @Inject(method = "canFallInLove", at = @At("HEAD"), cancellable = true)
    private void hexlove$refuseToFallInLove(CallbackInfoReturnable<Boolean> cir) {
        if (BondGate.isHeartbroken((Animal) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setInLove", at = @At("HEAD"), cancellable = true)
    private void hexlove$refuseSetInLove(@Nullable Player player, CallbackInfo ci) {
        if (BondGate.isHeartbroken((Animal) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "setInLoveTime", at = @At("HEAD"), cancellable = true)
    private void hexlove$refuseInLoveTime(int ticks, CallbackInfo ci) {
        if (ticks > 0 && BondGate.isHeartbroken((Animal) (Object) this)) {
            ci.cancel();
        }
    }
}
