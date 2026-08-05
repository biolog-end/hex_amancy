package io.github.teekas.hexlove.worldsoul

import io.github.teekas.hexlove.compat.AccessoryRingAccess
import io.github.teekas.hexlove.marriage.MarriageManager
import io.github.teekas.hexlove.marriage.RingData
import io.github.teekas.hexlove.registry.HexloveEffects
import io.github.teekas.hexlove.registry.HexloveItems
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobType
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.item.ItemStack

/** Central rules for earning charge and receiving the world's blessing. */
object WorldAffection {
    const val HOSTILE_KILL_CHARGE = 0.30
    const val FEED_CHARGE = 0.15
    const val BREED_CHARGE = 0.75
    const val TAME_CHARGE = 1.50
    const val TRADE_CHARGE = 0.60
    const val INITIAL_EMBRACE_TICKS = 24_000
    const val CRYSTAL_EMBRACE_TICKS = 6_000

    fun onHostileKilled(victim: LivingEntity, source: DamageSource) {
        val killer = source.entity as? ServerPlayer ?: return
        if (isHostile(victim)) reward(killer, HOSTILE_KILL_CHARGE)
    }

    @JvmStatic
    fun onAnimalFed(player: ServerPlayer) {
        reward(player, FEED_CHARGE)
    }

    @JvmStatic
    fun onAnimalBred(first: Animal, second: Animal) {
        val rewarded = HashSet<java.util.UUID>()
        listOfNotNull(first.loveCause, second.loveCause).forEach { player ->
            if (rewarded.add(player.uuid)) reward(player, BREED_CHARGE)
        }
    }

    @JvmStatic
    fun onAnimalTamed(player: ServerPlayer) {
        reward(player, TAME_CHARGE)
    }

    @JvmStatic
    fun onVillagerTrade(player: ServerPlayer) {
        reward(player, TRADE_CHARGE)
    }

    /**
     * The actor's own linked ring receives the full value. An active spouse's carried linked ring
     * receives half, making marriage a useful bridge without turning this into a second marriage.
     */
    fun reward(player: ServerPlayer, amount: Double) {
        if (amount <= 0.0) return
        addToCarriedRing(player, amount)
        val spouseId = MarriageManager.spouseOf(player.server, player.uuid, requireActive = true) ?: return
        val spouse = player.server.playerList.getPlayer(spouseId) ?: return
        addToCarriedRing(spouse, amount * 0.5)
    }

    fun bless(player: ServerPlayer, ticks: Int, shareWithSpouse: Boolean = true) {
        if (ticks <= 0) return
        extendEffect(player, ticks)
        if (!shareWithSpouse) return
        val spouseId = MarriageManager.spouseOf(player.server, player.uuid, requireActive = true) ?: return
        player.server.playerList.getPlayer(spouseId)?.let { extendEffect(it, ticks / 2) }
    }

    @JvmStatic
    fun hostileDamage(target: LivingEntity, source: DamageSource, amount: Float): Float {
        val player = source.entity as? ServerPlayer ?: return amount
        if (!player.hasEffect(HexloveEffects.WORLDS_EMBRACE.value) || !isHostile(target)) return amount
        return amount * 1.20f
    }

    fun isHostile(entity: LivingEntity): Boolean =
        entity is Enemy || entity.mobType == MobType.UNDEAD

    private fun addToCarriedRing(player: ServerPlayer, amount: Double): Boolean {
        val ring = carriedInventoryRings(player).firstOrNull {
            it.`is`(HexloveItems.SOULBOUND_RING.value) &&
                RingData.boundTo(it) == player.uuid &&
                RingData.isWorldLinked(it, player.uuid)
        }
        if (ring != null) {
            if (RingData.addWorldCharge(ring, amount) <= 0.0) return false
            player.inventory.setChanged()
            player.inventoryMenu.broadcastChanges()
            player.containerMenu.broadcastChanges()
            return true
        }

        val equipped = AccessoryRingAccess.equippedRings(player).firstOrNull {
            it.stack.`is`(HexloveItems.SOULBOUND_RING.value) &&
                RingData.boundTo(it.stack) == player.uuid &&
                RingData.isWorldLinked(it.stack, player.uuid)
        } ?: return false
        if (RingData.addWorldCharge(equipped.stack, amount) <= 0.0) return false
        equipped.markChanged()
        return true
    }

    private fun carriedInventoryRings(player: ServerPlayer): Sequence<ItemStack> = sequence {
        yieldAll(player.inventory.items)
        yieldAll(player.inventory.offhand)
    }

    private fun extendEffect(player: ServerPlayer, ticks: Int) {
        val effect = HexloveEffects.WORLDS_EMBRACE.value
        val current = player.getEffect(effect)?.duration ?: 0
        val duration = (current.toLong() + ticks).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        player.addEffect(MobEffectInstance(effect, duration, 0, false, true, true))
    }
}
