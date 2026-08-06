package io.github.teekas.hexlove.worldsoul

import io.github.teekas.hexlove.compat.AccessoryRingAccess
import io.github.teekas.hexlove.marriage.MarriageManager
import io.github.teekas.hexlove.marriage.RingData
import io.github.teekas.hexlove.registry.HexloveEffects
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.world.HexloveWorldData
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobType
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.item.ItemStack
import java.util.UUID

/**
 * Central rules for earning charge and receiving the world's blessing.
 *
 * The charge itself lives in [HexloveWorldData] keyed by the soul (player UUID), so a
 * second ring bound to the same player never forks the pool and unlinking one ring never
 * lets the first-attunement bonus be re-granted through another. Rings carry a mirror
 * value in NBT so tooltips and the item's client visual keep working exactly as before.
 */
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
        val rewarded = HashSet<UUID>()
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
     * The actor's own soul receives the full value. An active spouse's soul receives half,
     * making marriage a useful bridge without turning the spouse's link into a second
     * charge pool. Both credits require the recipient to currently carry at least one ring
     * that is world-linked to their own soul -- an unlinked player cannot passively hoard
     * charge waiting for a ring to arrive.
     */
    fun reward(player: ServerPlayer, amount: Double) {
        if (amount <= 0.0) return
        creditSoul(player, amount)
        val spouseId = MarriageManager.spouseOf(player.server, player.uuid, requireActive = true) ?: return
        val spouse = player.server.playerList.getPlayer(spouseId) ?: return
        creditSoul(spouse, amount * 0.5)
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

    /**
     * True while the player carries at least one ring whose world-soul points at their UUID.
     * Every hostile kill asks this, so it walks the slots directly instead of building a sequence.
     */
    fun hasAnyLinkedRing(player: ServerPlayer): Boolean {
        for (stack in player.inventory.items) if (isLinkedRing(stack, player.uuid)) return true
        for (stack in player.inventory.offhand) if (isLinkedRing(stack, player.uuid)) return true
        for (equipped in AccessoryRingAccess.equippedRings(player)) {
            if (isLinkedRing(equipped.stack, player.uuid)) return true
        }
        return false
    }

    private fun isLinkedRing(stack: ItemStack, owner: UUID): Boolean =
        stack.`is`(HexloveItems.SOULBOUND_RING.value) && RingData.isWorldLinked(stack, owner)

    /**
     * Copies the shared soul charge into every world-linked ring the player currently carries.
     * The NBT value is a snapshot for tooltips and the item's client model, never authoritative.
     */
    fun syncChargeNbt(player: ServerPlayer): Boolean {
        val soulCharge = HexloveWorldData.get(player.server).soulWorldCharge(player.uuid)
        var changed = false
        for (ring in player.inventory.items) {
            if (writeChargeNbt(ring, player.uuid, soulCharge)) changed = true
        }
        for (ring in player.inventory.offhand) {
            if (writeChargeNbt(ring, player.uuid, soulCharge)) changed = true
        }
        if (changed) {
            player.inventory.setChanged()
            player.inventoryMenu.broadcastChanges()
            player.containerMenu.broadcastChanges()
        }
        for (equipped in AccessoryRingAccess.equippedRings(player)) {
            if (writeChargeNbt(equipped.stack, player.uuid, soulCharge)) equipped.markChanged()
        }
        return changed
    }

    /**
     * One-shot import of legacy per-ring charge/attunement into the soul pool, safe to call
     * every time a ring is presented at an altar. Delegates to [HexloveWorldData.migrateLegacyRing],
     * which only ever raises, never lowers, the pool.
     */
    fun migrateLegacyRing(server: MinecraftServer, owner: UUID, ring: ItemStack) {
        val legacyAttuned = RingData.hasWorldAttunementHistory(ring)
        val legacyCharge = RingData.worldCharge(ring)
        if (!legacyAttuned && legacyCharge <= 0.0) return
        HexloveWorldData.get(server).migrateLegacyRing(owner, legacyAttuned, legacyCharge)
    }

    private fun creditSoul(player: ServerPlayer, amount: Double): Boolean {
        if (!hasAnyLinkedRing(player)) return false
        val gained = HexloveWorldData.get(player.server).addSoulWorldCharge(player.uuid, amount)
        if (gained <= 0.0) return false
        syncChargeNbt(player)
        return true
    }

    private fun writeChargeNbt(ring: ItemStack, owner: UUID, soulCharge: Double): Boolean {
        if (!ring.`is`(HexloveItems.SOULBOUND_RING.value)) return false
        if (!RingData.isWorldLinked(ring, owner)) return false
        val before = RingData.worldCharge(ring)
        if (before == soulCharge) return false
        ring.orCreateTag.putDouble("hexlove:world_charge", soulCharge)
        return true
    }

    private fun extendEffect(player: ServerPlayer, ticks: Int) {
        val effect = HexloveEffects.WORLDS_EMBRACE.value
        val current = player.getEffect(effect)?.duration ?: 0
        val duration = (current.toLong() + ticks).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        player.addEffect(MobEffectInstance(effect, duration, 0, false, true, true))
    }
}
