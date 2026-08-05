package io.github.teekas.hexlove.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Slimes and magma cubes do not walk. Their pathfinder is a stub and their move control ignores a
 * wanted position entirely: all steering goes through the hop direction it stores itself. Without
 * this, a charmed magma cube stood exactly where it was charmed and a charmed slime hopped at random
 * — it was following its beloved as hard as it was able to, which was not at all.
 *
 * An invoker rather than an access widener, because the class is package private and only reachable
 * through the mob's own move control instance. The interface doubles as the feature test: anything
 * whose move control is one of these is steered by hops, whatever mod added it.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Slime$SlimeMoveControl")
public interface MixinSlimeMoveControl {
    @Invoker("setDirection")
    void hexloveSetDirection(float yRot, boolean aggressive);

    @Invoker("setWantedMovement")
    void hexloveSetWantedMovement(double speed);
}
