package io.github.teekas.hexlove.registry

import io.github.teekas.hexlove.Hexlove
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundEvent

/** Sounds owned by Hex: Amancy and shared by both loaders. */
object HexloveSounds : HexloveRegistrar<SoundEvent>(
    Registries.SOUND_EVENT,
    { BuiltInRegistries.SOUND_EVENT },
) {
    val LOVE_HEARTBEAT = register("love_heartbeat") {
        SoundEvent.createVariableRangeEvent(Hexlove.id("love_heartbeat"))
    }
}
