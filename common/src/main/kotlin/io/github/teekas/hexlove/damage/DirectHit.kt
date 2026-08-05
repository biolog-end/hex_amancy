package io.github.teekas.hexlove.damage

import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity

/**
 * Requirements 9.4, 21.10: only a *deliberate blow* is blocked. An arrow, a snowball, TNT, lava,
 * a falling anvil or a splash potion all either have a different `directEntity` or carry a tag, so
 * they land in full — a lover can still be killed by accident.
 */
object DirectHit {
    fun isDirect(source: DamageSource, attacker: Entity?): Boolean {
        if (attacker == null) return false
        if (source.directEntity !== attacker) return false
        if (source.`is`(DamageTypeTags.IS_PROJECTILE)) return false
        if (source.`is`(DamageTypeTags.IS_EXPLOSION)) return false
        if (source.`is`(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false
        return true
    }
}
