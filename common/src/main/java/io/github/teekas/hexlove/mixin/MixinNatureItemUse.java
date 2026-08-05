package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.DietVerdict;
import io.github.teekas.hexlove.nature.NatureHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item-use hook, narrow on purpose.
 *
 *   - Humiliated player: no item whose id namespace is "hexcasting" may be used.
 *   - Chewable non-food in the active diet: start the chew animation.
 *   - Real food outside the active diet: cancel so the eating animation never even starts.
 *     `finishUsingItem` is the safety net for anything that skips this (Trinkets etc.).
 *
 * Everything else — snowballs, eggs, bows, hex staves, blocks placed by right-click,
 * real food inside the diet — falls through untouched. An earlier version cancelled every
 * `use` of any item outside the diet tag, which broke all of the above.
 */
@Mixin(ItemStack.class)
public abstract class MixinNatureItemUse {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void hexlove$natureDietGate(
        Level level,
        Player player,
        InteractionHand hand,
        CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir
    ) {
        ItemStack stack = (ItemStack) (Object) this;

        if (NatureHooks.blocksHexItemForHumiliated(player, stack)) {
            cir.setReturnValue(InteractionResultHolder.fail(stack));
            return;
        }

        DietVerdict verdict = NatureHooks.dietVerdict(player, stack);
        if (verdict == null) return;

        switch (verdict) {
            case FORBIDDEN -> cir.setReturnValue(InteractionResultHolder.fail(stack));
            case CHEWABLE -> {
                if (player.canEat(false)) {
                    player.startUsingItem(hand);
                    cir.setReturnValue(InteractionResultHolder.consume(stack));
                } else {
                    cir.setReturnValue(InteractionResultHolder.fail(stack));
                }
            }
            case ALLOWED_FOOD, IRRELEVANT -> {
                // Nothing to do — vanilla resolves eating or the item's own action.
            }
        }
    }
}
