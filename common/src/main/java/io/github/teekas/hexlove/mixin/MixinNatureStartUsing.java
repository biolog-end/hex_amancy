package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.NatureHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ultimate safety net for the diet gate: refuse to even <em>start</em> chewing food that is
 * outside the eater's current nature. The stack.use gate should have already stopped this,
 * but in play testing the chew animation was still starting somehow — food would not be
 * consumed and hunger did not refill (proof the finishUsingItem block was firing), but the
 * player visibly ground away at it. Injecting into LivingEntity.startUsingItem is one level
 * deeper than Item.use and blocks every entry point at once.
 */
@Mixin(LivingEntity.class)
public abstract class MixinNatureStartUsing {

    @Inject(method = "startUsingItem", at = @At("HEAD"), cancellable = true)
    private void hexlove$blockForbiddenChew(InteractionHand hand, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !stack.isEdible()) return;

        if (NatureHooks.blocksFinishEating(self, stack)) {
            ci.cancel();
        }
    }
}
