package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.NatureHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * After vanilla `Player.eat` credits its own numbers, this injection tops the food bar up to
 * the diet table's values for the active nature. Only ever adds, never subtracts.
 */
@Mixin(Player.class)
public abstract class MixinNaturePlayerEat {

    @Inject(method = "eat", at = @At("RETURN"))
    private void hexlove$eatBonus(
        Level level,
        ItemStack stack,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        Player player = (Player) (Object) this;
        if (!level.isClientSide) {
            NatureHooks.onPlayerEat(player, stack, player.getFoodData());
        }
    }
}
