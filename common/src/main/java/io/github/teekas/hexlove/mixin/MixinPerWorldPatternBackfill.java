package io.github.teekas.hexlove.mixin;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.server.ScrungledPatternsSave;
import io.github.teekas.hexlove.world.PerWorldPatternAccess;
import io.github.teekas.hexlove.world.PerWorldPatternBackfill;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Hex writes the shapes of per-world patterns into the save once, when the world is created, and the
 * loader for an existing world never adds to them. A Great Spell of ours in somebody's older world is
 * therefore shapeless: uncastable, and fatal on the tick an Ancient Scroll rolled for it is held.
 * <p>
 * `open` is where every reader comes through, and it is the only place that knows the world seed, so
 * this is where the gaps get filled. The flag keeps it to one registry walk per loaded save.
 */
@Mixin(value = ScrungledPatternsSave.class, remap = false)
public abstract class MixinPerWorldPatternBackfill implements PerWorldPatternAccess {
    @Shadow
    @Final
    private Map<String, ScrungledPatternsSave.PerWorldEntry> lookup;

    @Shadow
    @Final
    private Map<ResourceKey<ActionRegistryEntry>, String> reverseLookup;

    @Unique
    private boolean hexlove$backfilled = false;

    @NotNull
    @Override
    public Map<String, ScrungledPatternsSave.PerWorldEntry> hexloveLookup() {
        return this.lookup;
    }

    @NotNull
    @Override
    public Map<ResourceKey<ActionRegistryEntry>, String> hexloveReverseLookup() {
        return this.reverseLookup;
    }

    @Override
    public boolean hexloveBackfilled() {
        return this.hexlove$backfilled;
    }

    @Override
    public void hexloveMarkBackfilled() {
        this.hexlove$backfilled = true;
    }

    @Inject(method = "open", at = @At("RETURN"))
    private static void hexlove$backfillOnOpen(ServerLevel overworld,
        CallbackInfoReturnable<ScrungledPatternsSave> cir) {
        PerWorldPatternBackfill.ensure(cir.getReturnValue(), overworld.getSeed());
    }
}
