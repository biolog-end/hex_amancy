package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.NatureHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stage 3: triggers the humiliation advance when a serene player feeds an adult Cow/Sheep/Pig
 * and the two-step condition window is still open.
 *
 * MixinAnimalWorldAffection also injects at RETURN in the same method. Both injectors run
 * independently; each has its own @Unique guard field so they never interfere.
 *
 * Server-side check: the actual beginAdvance call is guarded by isClientSide inside NatureHooks.
 */
@Mixin(Animal.class)
public abstract class MixinNatureAnimalAdvance {

    @Inject(method = "mobInteract", at = @At("RETURN"))
    private void hexlove$advanceOnFeed(
        Player player,
        InteractionHand hand,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        NatureHooks.onAnimalAdvance((Animal) (Object) this, player, cir.getReturnValue().consumesAction());
    }
}
