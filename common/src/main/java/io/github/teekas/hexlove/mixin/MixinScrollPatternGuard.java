package io.github.teekas.hexlove.mixin;

import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.common.casting.PatternRegistryManifest;
import at.petrak.hexcasting.common.items.storage.ItemScroll;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import io.github.teekas.hexlove.Hexlove;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A scroll that names an action the world has no shape for kills the server: Hex resolves the shape on
 * the inventory tick and dereferences the result without checking it. That normally cannot happen, and
 * with {@link io.github.teekas.hexlove.world.PerWorldPatternBackfill} in place it should not happen for
 * our Great Spells either — but a scroll left behind by an uninstalled addon, or a backfill that could
 * not do its job, would still take the world down once a second.
 * <p>
 * Clearing the unresolvable id is what Hex itself does for an unparseable one, so the scroll goes blank
 * instead of crashing, and the reason ends up in the log.
 */
@Mixin(value = ItemScroll.class, remap = false)
public abstract class MixinScrollPatternGuard {
    @Inject(method = "inventoryTick", at = @At("HEAD"), remap = true)
    private void hexlove$dropUnresolvablePattern(ItemStack stack, Level level, Entity entity, int slot,
        boolean selected, CallbackInfo ci) {
        var server = entity.getServer();
        if (server == null) {
            return;
        }
        if (!NBTHelper.hasString(stack, ItemScroll.TAG_OP_ID)) {
            return;
        }

        var opID = ResourceLocation.tryParse(NBTHelper.getString(stack, ItemScroll.TAG_OP_ID));
        if (opID == null) {
            // Hex already handles this case, and handles it the same way.
            return;
        }

        // an already-resolved pattern is no protection: Hex reads the id first and dereferences the
        // lookup either way, which is exactly how the server went down once a second
        var where = level instanceof ServerLevel serverLevel ? serverLevel : server.overworld();
        var key = ResourceKey.create(IXplatAbstractions.INSTANCE.getActionRegistry().key(), opID);
        if (PatternRegistryManifest.getCanonicalStrokesPerWorld(key, where) != null) {
            return;
        }

        Hexlove.LOGGER.error(
            "A scroll names the action {}, which has no shape in this world. Clearing the name instead of "
                + "crashing; the scroll is now blank.", opID);
        NBTHelper.remove(stack, ItemScroll.TAG_OP_ID);
    }
}
