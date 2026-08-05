package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.worldsoul.WorldAffection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rewards accepted breeding food only when it was offered to an adult animal. */
@Mixin(Animal.class)
public abstract class MixinAnimalWorldAffection {
    @Unique
    private boolean hexlove$heldAdultAnimalFood;

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void hexlove$rememberAnimalFood(
        Player player,
        InteractionHand hand,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        Animal animal = (Animal) (Object) this;
        // Capture age before vanilla handles the item: the last growth feed can turn a baby into
        // an adult by RETURN, but it must still remain ineligible for world affection.
        hexlove$heldAdultAnimalFood = !animal.isBaby() && animal.isFood(player.getItemInHand(hand));
    }

    @Inject(method = "mobInteract", at = @At("RETURN"))
    private void hexlove$rewardAcceptedFood(
        Player player,
        InteractionHand hand,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (hexlove$heldAdultAnimalFood && cir.getReturnValue().consumesAction() &&
            player instanceof ServerPlayer serverPlayer
        ) {
            WorldAffection.onAnimalFed(serverPlayer);
        }
        hexlove$heldAdultAnimalFood = false;
    }
}
