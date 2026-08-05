package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.worldsoul.WorldAffection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Taking the result is the authoritative point at which a merchant trade has completed. */
@Mixin(MerchantResultSlot.class)
public abstract class MixinTradeWorldAffection {
    @Inject(method = "onTake", at = @At("TAIL"))
    private void hexlove$rewardTrade(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            WorldAffection.onVillagerTrade(serverPlayer);
        }
    }
}
