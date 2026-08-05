package io.github.teekas.hexlove.marriage

import io.github.teekas.hexlove.compat.AccessoryRingAccess
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.world.HexloveWorldData
import io.github.teekas.hexlove.world.MarriageRecord
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** Runtime rules for a marriage. A record alone is not a benefit: the original two rings must be carried or worn. */
object MarriageManager {
    fun record(server: MinecraftServer, player: UUID): MarriageRecord? =
        HexloveWorldData.get(server).marriageOf(player)

    fun spouseOf(server: MinecraftServer, player: UUID, requireActive: Boolean = true): UUID? {
        val record = record(server, player) ?: return null
        if (!record.contains(player) || (requireActive && !isActive(server, record))) return null
        return record.other(player)
    }

    fun isActive(server: MinecraftServer, record: MarriageRecord): Boolean =
        record.active && holdsOwnRing(server.playerList.getPlayer(record.a), record.a, record.b) &&
            holdsOwnRing(server.playerList.getPlayer(record.b), record.b, record.a)

    fun isActive(server: MinecraftServer, player: UUID): Boolean =
        record(server, player)?.let { isActive(server, it) } == true

    /** Used by vow-only spells that must reject an online spouse whose matching ring is absent. */
    fun carriesOwnRing(server: MinecraftServer, player: ServerPlayer): Boolean {
        val record = record(server, player.uuid) ?: return false
        return record.contains(player.uuid) && holdsOwnRing(player, player.uuid, record.other(player.uuid))
    }

    /**
     * Persists the inventory check. In particular, a player cannot throw their ring away and make
     * the bond become active again merely by logging out before somebody next queries it.
     */
    fun refresh(server: MinecraftServer) {
        val data = HexloveWorldData.get(server)
        val onlineWithOwnRing = if (data.marriages.isEmpty()) null else HashSet<UUID>()
        for (player in server.playerList.players) {
            if (synchronizeRings(player, data, server)) onlineWithOwnRing?.add(player.uuid)
        }
        for ((player, record) in data.marriages) {
            // Each record is indexed by both spouses. Process its canonical first key once without
            // allocating a distinct-values set on every server tick.
            if (player != record.a) continue
            val first = server.playerList.getPlayer(record.a)
            val second = server.playerList.getPlayer(record.b)
            val firstHasRing = first == null || onlineWithOwnRing?.contains(record.a) == true
            val secondHasRing = second == null || onlineWithOwnRing?.contains(record.b) == true

            // An online spouse without their own ring always deactivates the pair. The false state
            // must survive both players logging out; otherwise throwing a ring away and leaving the
            // server would make the marriage silently reactivate.
            if ((first != null && !firstHasRing) || (second != null && !secondHasRing)) {
                data.setMarriageActive(record.a, false)
                continue
            }

            // An already-active bond remains active when one partner is away. A deactivated one,
            // however, can only prove that its lost ring was recovered once both owners are online.
            if (!record.active && first != null && second != null && firstHasRing && secondHasRing) {
                data.setMarriageActive(record.a, true)
            }
        }
    }

    /** Offline spouses deliberately count as present: the marriage is not a login-state buff. */
    private fun holdsOwnRing(player: ServerPlayer?, owner: UUID, spouse: UUID): Boolean {
        if (player == null) return true
        for (stack in player.inventory.items) {
            if (isOwnRing(stack, owner, spouse)) return true
        }
        for (stack in player.inventory.offhand) {
            if (isOwnRing(stack, owner, spouse)) return true
        }
        return AccessoryRingAccess.equippedRings(player).any { isOwnRing(it.stack, owner, spouse) }
    }

    fun isOwnRing(stack: ItemStack, owner: UUID, spouse: UUID): Boolean =
        stack.`is`(HexloveItems.SOULBOUND_RING.value) && RingData.matches(stack, owner, spouse)

    /**
     * World data wins over portable NBT. This makes separation persistent for an offline spouse:
     * their old ring is reduced to a soul-bound ring the next time they join, rather than becoming
     * a stray second marriage. It also migrates old married rings to their record's pair colour.
     */
    private fun synchronizeRings(
        player: ServerPlayer,
        data: HexloveWorldData,
        server: MinecraftServer,
    ): Boolean {
        val record = data.marriageOf(player.uuid)
        val spouse = record?.other(player.uuid)
        val inMainInventory = synchronizeRings(
            player.inventory.items, player, record, spouse, server,
        )
        synchronizeRings(player.inventory.armor, player, record, spouse, server)
        val inOffhand = synchronizeRings(
            player.inventory.offhand, player, record, spouse, server,
        )
        val inAccessorySlot = synchronizeAccessoryRings(
            AccessoryRingAccess.equippedRings(player), player, record, spouse, server,
        )
        return inMainInventory || inOffhand || inAccessorySlot
    }

    private fun synchronizeAccessoryRings(
        rings: Iterable<AccessoryRingAccess.EquippedRing>,
        player: ServerPlayer,
        record: MarriageRecord?,
        spouse: UUID?,
        server: MinecraftServer,
    ): Boolean {
        var holdsOwnRing = false
        for (equipped in rings) {
            val result = synchronizeRing(equipped.stack, player, record, spouse, server)
            if (result.changed) equipped.markChanged()
            holdsOwnRing = holdsOwnRing || result.holdsOwnRing
        }
        return holdsOwnRing
    }

    private fun synchronizeRings(
        rings: Iterable<ItemStack>,
        player: ServerPlayer,
        record: MarriageRecord?,
        spouse: UUID?,
        server: MinecraftServer,
    ): Boolean {
        var holdsOwnRing = false
        for (ring in rings) {
            val result = synchronizeRing(ring, player, record, spouse, server)
            holdsOwnRing = holdsOwnRing || result.holdsOwnRing
        }
        return holdsOwnRing
    }

    private fun synchronizeRing(
        ring: ItemStack,
        player: ServerPlayer,
        record: MarriageRecord?,
        spouse: UUID?,
        server: MinecraftServer,
    ): RingSyncResult {
        if (!ring.`is`(HexloveItems.SOULBOUND_RING.value) || RingData.boundTo(ring) != player.uuid) {
            return RingSyncResult()
        }
        if (record == null) return RingSyncResult(changed = RingData.release(ring))
        spouse ?: return RingSyncResult()
        val spouseName = server.playerList.getPlayer(spouse)?.gameProfile?.name
            ?: server.profileCache?.get(spouse)?.orElse(null)?.name
        // This also gives legacy rings their portable names, so their client tooltip stays
        // readable outside an integrated server.
        val changed = RingData.marry(
            ring,
            player.uuid,
            spouse,
            record.colour,
            record.accentColour,
            player.gameProfile.name,
            spouseName,
        )
        return RingSyncResult(holdsOwnRing = true, changed = changed)
    }

    private data class RingSyncResult(
        val holdsOwnRing: Boolean = false,
        val changed: Boolean = false,
    )

    /** A married player may only be made to love or guard their own spouse. */
    fun permitsAffection(server: MinecraftServer, subject: Player, beloved: UUID): Boolean {
        val spouse = spouseOf(server, subject.uuid) ?: return true
        return spouse == beloved
    }
}
