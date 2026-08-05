package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.NatureHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stage 4: a ferocious player cannot trade with villagers.
 *
 * Injected at HEAD, cancellable. Returning FAIL prevents the trading GUI from opening.
 */
@Mixin(Villager.class)
public abstract class MixinNatureVillagerTrade {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void hexlove$blockFerociousTrade(
        Player player,
        InteractionHand hand,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        try {
            if (NatureHooks.blocksFerociousTrade(player)) {
                cir.setReturnValue(InteractionResult.FAIL);
            }
        } catch (Throwable t) {
            // Never let an exception reach vanilla
        }
    }
}
