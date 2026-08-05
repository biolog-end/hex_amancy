package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.NatureHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stage 4: a ferocious player is seen as hostile by villager sensors.
 *
 * VillagerHostilesSensor.isHostile(LivingEntity) is the method the sensor uses to populate
 * NEAREST_HOSTILE in a villager's brain. Returning true here causes the vanilla sensor to
 * set NEAREST_HOSTILE to this player, and the villager's brain panics on its own —
 * we do not need to touch villager brains directly.
 *
 * Verified method descriptor for 1.20.1: protected boolean isHostile(LivingEntity)
 */
@Mixin(VillagerHostilesSensor.class)
public abstract class MixinNatureVillagerSensor {

    @Inject(method = "isHostile", at = @At("RETURN"), cancellable = true)
    private void hexlove$ferociousIsHostile(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (Boolean.TRUE.equals(cir.getReturnValue())) return; // already hostile
            if (NatureHooks.isFerociousHostile(entity)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            // Never let an exception reach vanilla
        }
    }
}
