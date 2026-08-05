package io.github.teekas.hexlove.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.teekas.hexlove.nature.NatureHooks;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;

/**
 * Animals do not flee from a serene player.
 *
 * The target-selection veto in BondGate covers who a mob may attack; fleeing runs through a
 * separate path, so AvoidEntityGoal has to be told as well. Forcing false in both canUse and
 * canContinueToUse stops the goal from starting and cuts short a flight already in progress.
 *
 * Both fields are declared by AvoidEntityGoal itself, so Mixin remaps them; reading them by
 * reflection would break as soon as the names are obfuscated.
 */
@Mixin(AvoidEntityGoal.class)
public abstract class MixinNatureAvoidGoal {

    @Shadow
    protected PathfinderMob mob;

    /** Erased to LivingEntity: AvoidEntityGoal declares this as its generic parameter. */
    @Shadow
    protected LivingEntity toAvoid;

    @Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
    private void hexlove$noFleeFromSerene(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && NatureHooks.calmsAvoidance(this.mob, this.toAvoid)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
    private void hexlove$noFleeFromSereneOngoing(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && NatureHooks.calmsAvoidance(this.mob, this.toAvoid)) {
            cir.setReturnValue(false);
        }
    }
}
