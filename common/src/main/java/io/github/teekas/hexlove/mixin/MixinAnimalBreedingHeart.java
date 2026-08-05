package io.github.teekas.hexlove.mixin;

import io.github.teekas.hexlove.block.AmethystHeartManager;
import io.github.teekas.hexlove.worldsoul.WorldAffection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This precise point runs only after vanilla has accepted a child and just before it is inserted
 * into the world. It therefore includes Animal Passion's eventual vanilla courtship but not a
 * cancelled Forge breeding event.
 */
@Mixin(Animal.class)
public abstract class MixinAnimalBreedingHeart {
    @Inject(
        method = "spawnChildFromBreeding",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"
        )
    )
    private void hexlove$gatherLoveMedia(ServerLevel level, Animal partner, CallbackInfo ci) {
        AmethystHeartManager.onAnimalBred(level, (Animal) (Object) this, partner);
        WorldAffection.onAnimalBred((Animal) (Object) this, partner);
    }
}
