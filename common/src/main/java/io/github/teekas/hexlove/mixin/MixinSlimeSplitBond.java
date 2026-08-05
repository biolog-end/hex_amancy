package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.bond.BondGate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A slime that dies large leaves smaller ones behind, and they are new entities: whatever the whole
 * slime felt, the pieces knew nothing about. Cutting a charmed slime used to turn one devoted creature
 * into four indifferent ones, which reads as the charm breaking.
 * <p>
 * Injected at the return of {@code remove}, by which point the pieces are already in the level, and
 * they are picked out by their age: a slime that has not been ticked once was born from this split.
 * Deliberately not a redirect of the spawn call — an injection point inside the method body is one
 * more thing that can stop matching, and this hook is not worth a startup crash.
 */
@Mixin(Slime.class)
public abstract class MixinSlimeSplitBond {
    @Inject(method = "remove", at = @At("RETURN"))
    private void hexlove$passBondToPieces(Entity.RemovalReason reason, CallbackInfo ci) {
        BondGate.inheritOnSplit((Slime) (Object) this);
    }
}
