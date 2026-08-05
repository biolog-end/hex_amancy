package io.github.teekas.hexlove.world

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity

/** Settles deferred tithe payments as soon as their source is loaded again. */
object DebtLedger {
    fun settleOnLoad(entity: LivingEntity) {
        val server = (entity.level() as? ServerLevel)?.server ?: return
        val data = HexloveWorldData.get(server)
        val debt = data.consumeDebt(entity.uuid) ?: return
        if (!entity.isAlive || debt.amount <= 0f) return

        // The record is removed before damage, so a lethal hit cannot recurse. A promised source
        // pays whether the recipient is still online, alive, or even a player at all; missing
        // healing simply burns away, exactly like health beyond a living recipient's maximum.
        val before = entity.health
        entity.hurt(entity.damageSources().magic(), debt.amount)
        val actuallyPaid = (before - entity.health).coerceAtLeast(0f)
        val recipient = server.playerList.getPlayer(debt.creditor)
            ?: server.allLevels.asSequence().mapNotNull { it.getEntity(debt.creditor) as? LivingEntity }.firstOrNull()
        if (actuallyPaid > 0f && recipient?.isAlive == true) recipient.heal(actuallyPaid)
        val unpaid = debt.amount - actuallyPaid
        if (unpaid > 1.0e-4f && entity.isAlive) {
            data.addDebt(entity.uuid, unpaid, debt.creditor, debt.createdAt)
        }
    }

    /**
     * Fabric does not emit EntityEvent.ADD for creatures restored from chunk storage. Sweep only
     * the loaded entities which actually owe something, so such a creature still pays shortly
     * after any player brings its chunk back into view.
     */
    fun settleLoaded(level: ServerLevel) {
        val data = HexloveWorldData.get(level.server)
        if (data.debts.isEmpty()) return

        // Debt records already contain the exact UUIDs to look up. Avoid scanning every loaded
        // living entity in the level just to find this normally tiny set.
        for (uuid in data.debts.keys.toTypedArray()) {
            if (data.debtOf(uuid) <= 0f) continue
            val entity = level.getEntity(uuid) as? LivingEntity ?: continue
            if (entity.isAlive) settleOnLoad(entity)
        }
    }
}
