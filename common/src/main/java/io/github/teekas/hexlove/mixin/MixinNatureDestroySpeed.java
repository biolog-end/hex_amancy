package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.NatureHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stage 3: multiplies the dig speed return value by {@code nature.sereneDigSpeedMultiplier}
 * (default 0.8 = −20 %) when the player is serene.
 *
 * Injected at RETURN so all vanilla modifiers have already been applied; we then scale the final
 * result without touching any intermediate state.
 */
@Mixin(Player.class)
public abstract class MixinNatureDestroySpeed {

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void hexlove$sereneDigSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        float mult = NatureHooks.digSpeedMultiplier((Player) (Object) this);
        if (mult != 1.0f) {
            cir.setReturnValue(cir.getReturnValue() * mult);
        }
    }
}
