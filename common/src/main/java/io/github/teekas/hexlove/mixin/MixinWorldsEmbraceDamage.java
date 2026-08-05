package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.worldsoul.WorldAffection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Changes only the amount entering LivingEntity.hurt; neutral targets keep the original value. */
@Mixin(LivingEntity.class)
public abstract class MixinWorldsEmbraceDamage {
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float hexlove$worldsEmbraceDamage(float amount, DamageSource source) {
        return WorldAffection.hostileDamage((LivingEntity) (Object) this, source, amount);
    }
}
