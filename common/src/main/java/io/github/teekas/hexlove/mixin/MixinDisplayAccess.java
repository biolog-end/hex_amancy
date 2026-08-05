package io.github.teekas.hexlove.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Narrow access to the synced visual properties vanilla keeps private on display entities. */
@Mixin(Display.class)
public interface MixinDisplayAccess {
    @Invoker("setTransformation")
    void hexloveSetTransformation(Transformation transformation);

    @Invoker("setBillboardConstraints")
    void hexloveSetBillboard(Display.BillboardConstraints constraints);
}
