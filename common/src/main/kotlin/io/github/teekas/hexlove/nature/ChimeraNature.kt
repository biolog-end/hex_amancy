package io.github.teekas.hexlove.nature

import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.registry.HexloveEffects
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

enum class ChimeraNature { SERENE, FEROCIOUS }

/**
 * Single read/write facade for chimera nature state. The state itself lives entirely in the
 * vanilla effect system — no additional NBT or packets needed.
 *
 * `apply` is the only write path. It enforces mutual exclusion in one place so callers never
 * have to think about it.
 */
object ChimeraNatures {

    fun of(entity: LivingEntity): ChimeraNature? = when {
        entity.hasEffect(HexloveEffects.SERENE_CHIMERA.value) -> ChimeraNature.SERENE
        entity.hasEffect(HexloveEffects.FEROCIOUS_CHIMERA.value) -> ChimeraNature.FEROCIOUS
        else -> null
    }

    fun isSerene(entity: LivingEntity): Boolean =
        entity.hasEffect(HexloveEffects.SERENE_CHIMERA.value)

    fun isFerocious(entity: LivingEntity): Boolean =
        entity.hasEffect(HexloveEffects.FEROCIOUS_CHIMERA.value)

    fun isHumiliated(entity: LivingEntity): Boolean =
        entity.hasEffect(HexloveEffects.HUMILIATION.value)

    fun remainingTicks(entity: LivingEntity, nature: ChimeraNature): Int {
        val effect = when (nature) {
            ChimeraNature.SERENE -> HexloveEffects.SERENE_CHIMERA.value
            ChimeraNature.FEROCIOUS -> HexloveEffects.FEROCIOUS_CHIMERA.value
        }
        return entity.getEffect(effect)?.duration ?: 0
    }

    /**
     * Grants [nature] for [ticks]. Removes the opposite nature first so the two can never
     * coexist, even if called from multiple places concurrently.
     */
    fun apply(player: Player, nature: ChimeraNature, ticks: Int) {
        if (!HexloveServerConfig.config.nature.allowChimeraNatures) return
        val (toApply, toRemove) = when (nature) {
            ChimeraNature.SERENE ->
                HexloveEffects.SERENE_CHIMERA.value to HexloveEffects.FEROCIOUS_CHIMERA.value
            ChimeraNature.FEROCIOUS ->
                HexloveEffects.FEROCIOUS_CHIMERA.value to HexloveEffects.SERENE_CHIMERA.value
        }
        player.removeEffect(toRemove)
        player.addEffect(MobEffectInstance(toApply, ticks, 0, false, true, true))
    }
}
