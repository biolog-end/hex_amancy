package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.NatureHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives non-food items in the diet tables a use duration, eating animation and finish handler.
 *
 * The guards `vanilla == 0` / `vanilla == NONE` mean these injections are silent for any item
 * that vanilla already handles as a food, weapon, tool, or block. They only activate for items
 * with no vanilla use — exactly what the chew mechanic needs, with no collateral changes.
 */
@Mixin(ItemStack.class)
public abstract class MixinNatureChew {

    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void hexlove$chewDuration(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() != 0) return;
        ItemStack stack = (ItemStack) (Object) this;
        if (!NatureHooks.isChewableByAnyNature(stack.getItem())) return;
        int ticks = NatureHooks.chewDuration(stack.getItem());
        if (ticks > 0) cir.setReturnValue(ticks);
    }

    @Inject(method = "getUseAnimation", at = @At("RETURN"), cancellable = true)
    private void hexlove$chewAnimation(CallbackInfoReturnable<UseAnim> cir) {
        if (cir.getReturnValue() != UseAnim.NONE) return;
        ItemStack stack = (ItemStack) (Object) this;
        if (!NatureHooks.isChewableByAnyNature(stack.getItem())) return;
        cir.setReturnValue(UseAnim.EAT);
    }

    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    private void hexlove$finishChewing(
        Level level,
        LivingEntity entity,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        ItemStack stack = (ItemStack) (Object) this;

        // Safety net: any real food outside the eater's active diet is stopped here, even if
        // something else (a mod, a right-click on air handled elsewhere, a Trinket) has already
        // started the eat animation. Returning the same stack without shrinking or feeding.
        if (NatureHooks.blocksFinishEating(entity, stack)) {
            cir.setReturnValue(stack);
            return;
        }

        if (!NatureHooks.isChewableByAnyNature(stack.getItem())) return;
        ItemStack result = NatureHooks.finishChewing(stack, level, entity);
        if (result != null) cir.setReturnValue(result);
    }
}
