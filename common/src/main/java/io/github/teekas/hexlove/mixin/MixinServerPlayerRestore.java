package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.bond.BondHolder;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Requirement 3.5: death and respawn create a brand new ServerPlayer object, so the states have to
 * be copied over explicitly — unconditionally, whatever `keepInventory` says.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerRestore implements BondHolder {
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void hexlove$restoreBonds(ServerPlayer that, boolean keepEverything, CallbackInfo ci) {
        BondHolder old = (BondHolder) that;
        if (!old.hexloveHasBonds()) {
            return;
        }
        this.hexloveBonds().copyFrom(old.hexloveBonds());
    }
}
