package io.github.teekas.hexlove.registry

import io.github.teekas.hexlove.Hexlove
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.sounds.SoundEvent

/**
 * Sounds owned by Hex: Amancy and shared by both loaders.
 *
 * Every file behind these events is synthesised from scratch by `tools/generate_sounds.py`;
 * none of them is sampled from another mod. Spells deliberately have no sounds of their own —
 * Hex Casting already voices the cast itself.
 */
object HexloveSounds : HexloveRegistrar<SoundEvent>(
    Registries.SOUND_EVENT,
    { BuiltInRegistries.SOUND_EVENT },
) {
    private fun sound(name: String) = register(name) {
        SoundEvent.createVariableRangeEvent(Hexlove.id(name))
    }

    val LOVE_HEARTBEAT = sound("love_heartbeat")

    // The Chimera: one throat that never settled on a species.
    val CHIMERA_AMBIENT = sound("chimera_ambient")
    val CHIMERA_HURT = sound("chimera_hurt")
    val CHIMERA_DEATH = sound("chimera_death")
    val CHIMERA_STEP = sound("chimera_step")
    val CHIMERA_HEADBUTT = sound("chimera_headbutt")

    val AMETHYST_HEART_BEAT = sound("amethyst_heart_beat")
    val AMETHYST_HEART_CHARGE = sound("amethyst_heart_charge")
    val AMETHYST_HEART_CRYSTALLISE = sound("amethyst_heart_crystallise")
    val AMETHYST_HEART_OVERFLOW = sound("amethyst_heart_overflow")
    val AMETHYST_HEART_RESONATE = sound("amethyst_heart_resonate")

    val VOW_ALTAR_OFFER = sound("vow_altar_offer")
    val VOW_ALTAR_RITUAL = sound("vow_altar_ritual")
    val VOW_ALTAR_VOW = sound("vow_altar_vow")
    val VOW_ALTAR_SEVER = sound("vow_altar_sever")

    val WORLD_SOUL_AWAKEN = sound("world_soul_awaken")
    val WORLD_SOUL_ATTUNE_START = sound("world_soul_attune_start")
    val WORLD_SOUL_ATTUNE_SEAL = sound("world_soul_attune_seal")
    val WORLD_SOUL_ATTUNE_FINISH = sound("world_soul_attune_finish")
    val WORLD_SOUL_DETACH_START = sound("world_soul_detach_start")
    val WORLD_SOUL_DETACH_BREAK = sound("world_soul_detach_break")
    val WORLD_SOUL_DETACH_FINISH = sound("world_soul_detach_finish")
    val WORLD_SOUL_EMBODY_START = sound("world_soul_embody_start")
    val WORLD_SOUL_EMBODY_FORGE = sound("world_soul_embody_forge")
    val WORLD_SOUL_EMBODY_FINISH = sound("world_soul_embody_finish")
    val WORLD_SOUL_RITUAL_FAIL = sound("world_soul_ritual_fail")

    val CRYSTALLIZED_AFFECTION_STRAIN = sound("crystallized_affection_strain")
    val CRYSTALLIZED_AFFECTION_BURST = sound("crystallized_affection_burst")

    // One per reward tier: the burst is the shared unsealing, the open_* is the answer.
    val CRYSTALLIZED_AFFECTION_OPEN_SILENCE = sound("crystallized_affection_open_silence")
    val CRYSTALLIZED_AFFECTION_OPEN_SPARK = sound("crystallized_affection_open_spark")
    val CRYSTALLIZED_AFFECTION_OPEN_WORLD = sound("crystallized_affection_open_world")
    val CRYSTALLIZED_AFFECTION_OPEN_ECHO = sound("crystallized_affection_open_echo")
    val CRYSTALLIZED_AFFECTION_OPEN_LEGACY = sound("crystallized_affection_open_legacy")
}
