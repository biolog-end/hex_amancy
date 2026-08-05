package io.github.teekas.hexlove.client

import io.github.teekas.hexlove.registry.HexloveSounds
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource

/**
 * A quiet, private heartbeat heard only by a player under Cupid's Charm while their loaded beloved
 * is very close. The existing target-position sync is authoritative, so no extra packet or saved
 * state is needed.
 */
@Environment(EnvType.CLIENT)
object LoveHeartbeat {
    private const val HEARING_DISTANCE = 4.0
    private const val MIN_VOLUME = 0.04f
    private const val MAX_VOLUME = 0.12f

    private var activeSound: HeartbeatSound? = null

    fun tick(client: Minecraft) {
        val distance = distanceToBeloved(client)
        val current = activeSound

        if (distance == null || distance > HEARING_DISTANCE) {
            current?.finish()
            activeSound = null
            return
        }

        if (current == null || current.isStopped) {
            HeartbeatSound(client).also {
                activeSound = it
                client.soundManager.play(it)
            }
        }
    }

    fun stop() {
        activeSound?.finish()
        activeSound = null
    }

    private fun distanceToBeloved(client: Minecraft): Double? {
        if (!ClientBondState.hasLove || ClientBondState.heartbroken) return null
        val player = client.player ?: return null
        val target = ClientBondState.targetPosition ?: return null
        return player.position().distanceTo(target)
    }

    private fun volumeAt(distance: Double): Float {
        val closeness = (1.0 - distance / HEARING_DISTANCE).coerceIn(0.0, 1.0).toFloat()
        return MIN_VOLUME + (MAX_VOLUME - MIN_VOLUME) * closeness
    }

    private class HeartbeatSound(
        private val client: Minecraft,
    ) : AbstractTickableSoundInstance(
        HexloveSounds.LOVE_HEARTBEAT.value,
        SoundSource.PLAYERS,
        RandomSource.create(),
    ) {
        init {
            looping = true
            delay = 0
            volume = MIN_VOLUME
            pitch = 1.0f
            relative = true
            attenuation = SoundInstance.Attenuation.NONE
        }

        override fun tick() {
            val distance = distanceToBeloved(client)
            val player = client.player
            if (distance == null || distance > HEARING_DISTANCE || player == null) {
                stop()
                return
            }

            volume = volumeAt(distance)
            x = player.x
            y = player.y
            z = player.z
        }

        fun finish() {
            stop()
        }
    }
}
