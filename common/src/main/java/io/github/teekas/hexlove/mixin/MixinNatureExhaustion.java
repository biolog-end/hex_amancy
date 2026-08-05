package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.nature.NatureHooks;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Multiplies the food exhaustion value by the nature's metabolic rate before it reaches
 * FoodData. Serene nature burns slower (0.5×), ferocious nature burns faster (2.0×).
 */
@Mixin(Player.class)
public abstract class MixinNatureExhaustion {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float hexlove$metabolismMultiplier(float exhaustion) {
        return NatureHooks.multiplyExhaustion((Player) (Object) this, exhaustion);
    }
}
