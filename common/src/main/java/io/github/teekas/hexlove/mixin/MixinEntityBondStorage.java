package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.bond.BondData;
import io.github.teekas.hexlove.bond.BondHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Requirements 3.1, 3.2, 3.4, 3.6: bond states live on the entity itself, so they survive chunk
 * unloading, dimension changes and world saves for free. A common mixin is the only storage that
 * works on both loaders (Cardinal Components is Fabric-only, persistent data is Forge-only).
 */
@Mixin(Entity.class)
public abstract class MixinEntityBondStorage implements BondHolder {
    @Unique
    private BondData hexlove$bonds = null;

    @NotNull
    @Override
    public BondData hexloveBonds() {
        if (this.hexlove$bonds == null) {
            this.hexlove$bonds = new BondData();
        }
        return this.hexlove$bonds;
    }

    @Override
    public boolean hexloveHasBonds() {
        return this.hexlove$bonds != null && !this.hexlove$bonds.isEmpty();
    }

    /** An entity with no bonds writes nothing at all (Requirement 3.9). */
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void hexlove$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.hexlove$bonds == null || this.hexlove$bonds.isEmpty()) {
            return;
        }
        cir.getReturnValue().put(BondData.NBT_KEY, this.hexlove$bonds.save());
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void hexlove$load(CompoundTag tag, CallbackInfo ci) {
        if (!tag.contains(BondData.NBT_KEY)) {
            return;
        }
        this.hexloveBonds().load(tag.getCompound(BondData.NBT_KEY));
    }
}
