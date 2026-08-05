package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.worldsoul.WorldAffection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** tame is reached only after a vanilla or modded taming attempt has actually succeeded. */
@Mixin(TamableAnimal.class)
public abstract class MixinTamingWorldAffection {
    @Inject(method = "tame", at = @At("TAIL"))
    private void hexlove$rewardTaming(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            WorldAffection.onAnimalTamed(serverPlayer);
        }
    }
}
