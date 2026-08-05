package io.github.teekas.hexlove.mixin;

import at.petrak.hexcasting.common.items.magic.ItemCreativeUnlocker;
import io.github.teekas.hexlove.advancement.HexloveAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes Hex Casting's Media Cube unlock the complete Hex: Amancy advancement tree too. */
@Mixin(ItemCreativeUnlocker.class)
public abstract class MixinCreativeUnlockerAdvancements {
    @Inject(method = "finishUsingItem", at = @At("TAIL"))
    private void hexlove$grantAllAdvancements(
        ItemStack stack,
        Level level,
        LivingEntity consumer,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!level.isClientSide && consumer instanceof ServerPlayer player) {
            HexloveAdvancements.grantAll(player);
        }
    }
}
