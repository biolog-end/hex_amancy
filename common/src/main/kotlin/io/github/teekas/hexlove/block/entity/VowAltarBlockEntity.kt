package io.github.teekas.hexlove.block.entity

import io.github.teekas.hexlove.compat.AccessoryRingAccess
import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import io.github.teekas.hexlove.marriage.RingData
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.menu.VowAltarMenu
import io.github.teekas.hexlove.registry.HexloveBlockEntities
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.registry.HexloveSounds
import io.github.teekas.hexlove.world.HexloveWorldData
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.ContainerHelper
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The altar owns its offerings until it explicitly gives them back. That small bit of custody is
 * what makes every path safe: a ritual cannot duplicate a ring through a logout, and a separation
 * cannot leave one half of a marriage alive on the altar.
 */
class VowAltarBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(HexloveBlockEntities.VOW_ALTAR.value, pos, state), MenuProvider, Container {
    private val offeringStacks = MutableList(2) { ItemStack.EMPTY }
    private var first: ItemStack
        get() = offeringStacks[0]
        set(value) { offeringStacks[0] = value }
    private var second: ItemStack
        get() = offeringStacks[1]
        set(value) { offeringStacks[1] = value }
    private var offeredAt = 0L
    private var ritualStartedAt = 0L
    private val offeringPlacedAt = LongArray(2)
    /** Direction from the altar to the player who placed each offering, for its arrival animation. */
    private val offeringApproachX = DoubleArray(2)
    private val offeringApproachZ = DoubleArray(2)

    /** Interpolates an inserted ring from outside the altar into its ritual position. */
    fun offeringArrivalProgress(slot: Int, gameTime: Double): Double {
        val placedAt = offeringPlacedAt.getOrElse(slot) { 0L }
        if (placedAt == 0L) return 1.0
        return ((gameTime - placedAt) / OFFER_FLIGHT_TICKS).coerceIn(0.0, 1.0)
    }

    /** Remembers the actual placing player so left/right slots are left/right from their viewpoint. */
    fun notePlacement(player: Player, slot: Int) {
        if (player !is ServerPlayer || slot !in offeringStacks.indices) return
        val centre = Vec3.atCenterOf(worldPosition)
        val dx = player.x - centre.x
        val dz = player.z - centre.z
        val length = kotlin.math.sqrt(dx * dx + dz * dz)
        if (length > 0.001) {
            offeringApproachX[slot] = dx / length
            offeringApproachZ[slot] = dz / length
        }
    }

    /** A unit vector from this altar towards the player who placed the selected offering. */
    fun offeringApproach(slot: Int): Vec3 {
        val x = offeringApproachX.getOrElse(slot) { 0.0 }
        val z = offeringApproachZ.getOrElse(slot) { 1.0 }
        return if (x * x + z * z > 0.001) Vec3(x, 0.0, z) else Vec3(0.0, 0.0, 1.0)
    }

    fun ritualProgress(): Float {
        val now = level?.gameTime ?: return 0f
        if (ritualStartedAt == 0L) return 0f
        return ((now - ritualStartedAt).toFloat() / RITUAL_TICKS).coerceIn(0f, 1f)
    }

    fun isUsableBy(player: Player): Boolean = player.level() === level && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <=
        MENU_RADIUS * MENU_RADIUS

    override fun getDisplayName(): Component = Component.translatable("block.hexlove.vow_altar")

    override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
        VowAltarMenu(containerId, inventory, this)

    /** The menu calls this before any insertion, including shift-clicking and hotbar swaps. */
    fun canPlaceRing(player: Player, ring: ItemStack): Boolean {
        val serverPlayer = player as? ServerPlayer ?: return true
        if (!isUsableBy(serverPlayer) || !ring.`is`(HexloveItems.SOULBOUND_RING.value)) return false
        val owner = RingData.boundTo(ring) ?: return false
        if (owner != serverPlayer.uuid || hasOwner(owner)) return false

        val data = HexloveWorldData.get(serverPlayer.server)
        val marriage = data.marriageOf(owner)
        if (marriage != null) {
            // A married ring is allowed only as the sole offering that explicitly ends that vow.
            return first.isEmpty && second.isEmpty && RingData.matches(ring, owner, marriage.other(owner))
        }

        // Repair a tag left behind by an old, already-ended marriage before beginning a fresh vow.
        if (RingData.spouse(ring) != null) RingData.release(ring)
        return !RingData.isMarried(ring) && !isSeveringOffer()
    }

    fun canTakeRing(player: Player, ring: ItemStack): Boolean =
        isUsableBy(player) && RingData.boundTo(ring) == player.uuid

    /**
     * Requires the player's own married ring to be resting on this altar. The record is removed
     * first-class (from both UUID keys), then every online copy is reduced to a soul-only ring;
     * offline inventories are normalized when their owner next joins.
     */
    fun severVow(player: Player): Boolean {
        val serverPlayer = player as? ServerPlayer ?: return false
        if (!isUsableBy(serverPlayer) || !(first.isEmpty xor second.isEmpty)) return false
        val offered = if (first.isEmpty) second else first
        val owner = RingData.boundTo(offered) ?: return false
        if (owner != serverPlayer.uuid || RingData.spouse(offered) == null) return false

        val data = HexloveWorldData.get(serverPlayer.server)
        val record = data.marriageOf(owner) ?: return false
        if (!RingData.matches(offered, owner, record.other(owner))) return false

        data.removeMarriage(owner)
        releaseRingsOf(serverPlayer.server.playerList.getPlayer(record.a), record.a)
        releaseRingsOf(serverPlayer.server.playerList.getPlayer(record.b), record.b)
        RingData.release(offered)
        returnRings()

        val serverLevel = level as? ServerLevel ?: return true
        val centre = Vec3.atCenterOf(worldPosition)
        serverLevel.sendParticles(ParticleTypesHolder.PINK, centre.x, centre.y + 0.8, centre.z, 18, 0.45, 0.35, 0.45, 0.02)
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, centre.x, centre.y + 0.65, centre.z, 8, 0.4, 0.25, 0.4, 0.02)
        serverLevel.playSound(null, worldPosition, HexloveSounds.VOW_ALTAR_SEVER.value, SoundSource.BLOCKS, 1.0f, 1.0f)
        return true
    }

    private fun hasOwner(owner: UUID): Boolean = RingData.boundTo(first) == owner || RingData.boundTo(second) == owner

    private fun isSeveringOffer(): Boolean =
        first.isEmpty.xor(second.isEmpty) && RingData.spouse(if (first.isEmpty) second else first) != null

    private fun complete(level: ServerLevel, a: UUID, b: UUID) {
        val data = HexloveWorldData.get(level.server)
        // A vow does not replace two souls with a third colour. It joins the hues that each ring
        // already carried, giving both rings the same living two-colour gradient.
        val colour = RingData.soulAura(first)
        val accentColour = RingData.soulAura(second).takeUnless { it == colour } ?: RingData.companionAura(colour)
        if (!data.createMarriage(a, b, level.gameTime, colour, accentColour)) {
            returnRings()
            return
        }

        val firstName = level.server.playerList.getPlayer(a)?.gameProfile?.name
            ?: level.server.profileCache?.get(a)?.orElse(null)?.name
        val secondName = level.server.playerList.getPlayer(b)?.gameProfile?.name
            ?: level.server.profileCache?.get(b)?.orElse(null)?.name
        RingData.marry(first, a, b, colour, accentColour, firstName, secondName)
        RingData.marry(second, b, a, colour, accentColour, secondName, firstName)
        val firstPlayer = level.server.playerList.getPlayer(a)
        val secondPlayer = level.server.playerList.getPlayer(b)
        giveBack(firstPlayer, first)
        giveBack(secondPlayer, second)
        firstPlayer?.let { HexloveAdvancements.grant(it, HexloveAdvancements.TILL_SERVER_DO_US_PART) }
        secondPlayer?.let { HexloveAdvancements.grant(it, HexloveAdvancements.TILL_SERVER_DO_US_PART) }
        clearOffer()

        val centre = Vec3.atCenterOf(worldPosition)
        level.sendParticles(ParticleTypesHolder.PINK, centre.x, centre.y + 1.0, centre.z, 88, 1.2, 0.9, 1.2, 0.08)
        level.sendParticles(ParticleTypesHolder.RED, centre.x, centre.y + 0.95, centre.z, 56, 0.9, 0.75, 0.9, 0.055)
        level.sendParticles(ParticleTypesHolder.WHITE, centre.x, centre.y + 1.1, centre.z, 32, 0.65, 0.75, 0.65, 0.045)
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, centre.x, centre.y + 1.0, centre.z, 42, 1.1, 0.8, 1.1, 0.05)
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, centre.x, centre.y + 1.05, centre.z, 24, 0.75, 0.8, 0.75, 0.04)
        level.playSound(null, worldPosition, HexloveSounds.VOW_ALTAR_VOW.value, SoundSource.BLOCKS, 1.1f, 1.0f)
    }

    private fun releaseRingsOf(player: ServerPlayer?, owner: UUID) {
        if (player == null) return
        for (ring in player.inventory.items + player.inventory.armor + player.inventory.offhand) {
            if (ring.`is`(HexloveItems.SOULBOUND_RING.value) && RingData.boundTo(ring) == owner) RingData.release(ring)
        }
        for (equipped in AccessoryRingAccess.equippedRings(player)) {
            val ring = equipped.stack
            if (ring.`is`(HexloveItems.SOULBOUND_RING.value) &&
                RingData.boundTo(ring) == owner &&
                RingData.release(ring)
            ) {
                equipped.markChanged()
            }
        }
    }

    private fun giveBack(player: ServerPlayer?, ring: ItemStack) {
        if (ring.isEmpty) return
        if (player != null && player.addItem(ring)) {
            // RingData was just changed while the stack was owned by the altar. Explicitly flush
            // both inventory menus so the client receives the married NBT immediately instead of
            // keeping the inert model until the stack is moved or dropped.
            player.inventory.setChanged()
            player.inventoryMenu.broadcastChanges()
            player.containerMenu.broadcastChanges()
            return
        }
        val serverLevel = level as? ServerLevel ?: return
        Block.popResource(serverLevel, worldPosition, ring)
    }

    fun returnRings() {
        val server = (level as? ServerLevel)?.server
        giveBackToOwner(server, first)
        giveBackToOwner(server, second)
        clearOffer()
    }

    private fun giveBackToOwner(server: MinecraftServer?, ring: ItemStack) {
        val owner = RingData.boundTo(ring)
        giveBack(owner?.let { server?.playerList?.getPlayer(it) }, ring)
    }

    private fun clearOffer() {
        first = ItemStack.EMPTY
        second = ItemStack.EMPTY
        offeredAt = 0L
        ritualStartedAt = 0L
        offeringPlacedAt.fill(0L)
        offeringApproachX.fill(0.0)
        offeringApproachZ.fill(0.0)
        sync()
    }

    override fun getContainerSize(): Int = offeringStacks.size

    override fun isEmpty(): Boolean = offeringStacks.all(ItemStack::isEmpty)

    override fun getItem(slot: Int): ItemStack = offeringStacks.getOrElse(slot) { ItemStack.EMPTY }

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        val removed = ContainerHelper.removeItem(offeringStacks, slot, amount)
        if (!removed.isEmpty) {
            offeringPlacedAt[slot] = 0L
            offeringApproachX[slot] = 0.0
            offeringApproachZ[slot] = 0.0
            setChanged()
        }
        return removed
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        if (slot !in offeringStacks.indices) return ItemStack.EMPTY
        val removed = offeringStacks.set(slot, ItemStack.EMPTY)
        if (!removed.isEmpty) {
            offeringPlacedAt[slot] = 0L
            offeringApproachX[slot] = 0.0
            offeringApproachZ[slot] = 0.0
        }
        return removed
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot !in offeringStacks.indices) return
        val previous = offeringStacks[slot]
        offeringStacks[slot] = stack.copyWithCount(stack.count.coerceAtMost(maxStackSize))
        offeringPlacedAt[slot] = if (stack.isEmpty) 0L else if (previous.isEmpty) level?.gameTime ?: 0L else offeringPlacedAt[slot]
        if (stack.isEmpty) {
            offeringApproachX[slot] = 0.0
            offeringApproachZ[slot] = 0.0
        } else if (previous.isEmpty) {
            // The second ring lands a little brighter than the first, so an altar that is now
            // ready to marry sounds different from one still waiting.
            val second = !offeringStacks[0].isEmpty && !offeringStacks[1].isEmpty
            (level as? ServerLevel)?.playSound(
                null, worldPosition, HexloveSounds.VOW_ALTAR_OFFER.value, SoundSource.BLOCKS,
                0.8f, if (second) 1.18f else 1.0f,
            )
        }
        setChanged()
    }

    /** Slot changes begin (or cancel) the rite immediately, and update the client-side altar renderer. */
    override fun setChanged() {
        val now = level?.gameTime ?: 0L
        if (isEmpty) {
            offeredAt = 0L
        } else if (offeredAt == 0L) {
            offeredAt = now
        }
        ritualStartedAt = 0L
        sync()
    }

    override fun stillValid(player: Player): Boolean = isUsableBy(player)

    override fun clearContent() = clearOffer()

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        if (!first.isEmpty) tag.put("first", first.save(CompoundTag()))
        if (!second.isEmpty) tag.put("second", second.save(CompoundTag()))
        tag.putLong("offeredAt", offeredAt)
        tag.putLong("ritualStartedAt", ritualStartedAt)
        tag.putLong("firstPlacedAt", offeringPlacedAt[0])
        tag.putLong("secondPlacedAt", offeringPlacedAt[1])
        tag.putDouble("firstApproachX", offeringApproachX[0])
        tag.putDouble("firstApproachZ", offeringApproachZ[0])
        tag.putDouble("secondApproachX", offeringApproachX[1])
        tag.putDouble("secondApproachZ", offeringApproachZ[1])
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        first = if (tag.contains("first")) ItemStack.of(tag.getCompound("first")) else ItemStack.EMPTY
        second = if (tag.contains("second")) ItemStack.of(tag.getCompound("second")) else ItemStack.EMPTY
        offeredAt = if (tag.contains("offeredAt")) tag.getLong("offeredAt") else tag.getLong("startedAt")
        ritualStartedAt = tag.getLong("ritualStartedAt")
        offeringPlacedAt[0] = if (tag.contains("firstPlacedAt")) tag.getLong("firstPlacedAt") else 0L
        offeringPlacedAt[1] = if (tag.contains("secondPlacedAt")) tag.getLong("secondPlacedAt") else 0L
        offeringApproachX[0] = if (tag.contains("firstApproachX")) tag.getDouble("firstApproachX") else 0.0
        offeringApproachZ[0] = if (tag.contains("firstApproachZ")) tag.getDouble("firstApproachZ") else 0.0
        offeringApproachX[1] = if (tag.contains("secondApproachX")) tag.getDouble("secondApproachX") else 0.0
        offeringApproachZ[1] = if (tag.contains("secondApproachZ")) tag.getDouble("secondApproachZ") else 0.0
    }

    override fun getUpdateTag(): CompoundTag = CompoundTag().also(::saveAdditional)

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    private fun sync() {
        super.setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    companion object {
        private const val RITUAL_TICKS = 100L
        private const val OFFER_FLIGHT_TICKS = 12.0
        private const val RITUAL_RADIUS = 4.0
        private const val MENU_RADIUS = 6.0

        @JvmStatic
        fun tick(level: ServerLevel, pos: BlockPos, state: BlockState, altar: VowAltarBlockEntity) {
            if (altar.isSeveringOffer()) return
            val a = RingData.boundTo(altar.first) ?: return
            val b = RingData.boundTo(altar.second) ?: return
            if (a == b) return
            val firstPlayer = level.server.playerList.getPlayer(a)
            val secondPlayer = level.server.playerList.getPlayer(b)
            val centreX = pos.x + 0.5
            val centreY = pos.y + 0.5
            val centreZ = pos.z + 0.5
            val close = firstPlayer != null && secondPlayer != null &&
                firstPlayer.level() === level && secondPlayer.level() === level &&
                firstPlayer.distanceToSqr(centreX, centreY, centreZ) <= RITUAL_RADIUS * RITUAL_RADIUS &&
                secondPlayer.distanceToSqr(centreX, centreY, centreZ) <= RITUAL_RADIUS * RITUAL_RADIUS
            if (!close) {
                if (altar.ritualStartedAt != 0L) {
                    altar.ritualStartedAt = 0L
                    altar.sync()
                }
                return
            }
            if (altar.ritualStartedAt == 0L) {
                altar.ritualStartedAt = level.gameTime
                altar.sync()
                // Five seconds of two voices converging, exactly as long as RITUAL_TICKS: the
                // braid arrives at unison on the tick the vow is sealed.
                level.playSound(null, pos, HexloveSounds.VOW_ALTAR_RITUAL.value, SoundSource.BLOCKS, 0.85f, 1.0f)
            }
            val elapsed = level.gameTime - altar.ritualStartedAt
            ritualParticles(level, pos, elapsed)
            if (elapsed >= RITUAL_TICKS) altar.complete(level, a, b)
        }

        /** Bright motes, hearts and sparks spiral after the two offered rings as they join. */
        private fun ritualParticles(level: ServerLevel, pos: BlockPos, elapsed: Long) {
            if (elapsed % 2L != 0L) return
            val progress = (elapsed.toDouble() / RITUAL_TICKS).coerceIn(0.0, 1.0)
            val centre = Vec3.atCenterOf(pos)
            repeat(6) { strand ->
                val angle = elapsed * (0.24 + progress * 0.16) + 2.0 * PI * strand / 6.0
                val radius = 1.12 * (1.0 - progress * 0.58) * (0.78 + 0.22 * sin(angle * 1.7))
                val x = centre.x + cos(angle) * radius
                val z = centre.z + sin(angle) * radius
                val y = centre.y + 0.5 + progress * 1.05 + sin(angle * 2.0) * 0.12
                val particle = when (strand % 3) {
                    0 -> ParticleTypesHolder.PINK
                    1 -> ParticleTypesHolder.RED
                    else -> ParticleTypesHolder.WHITE
                }
                level.sendParticles(particle, x, y, z, 1, 0.025, 0.025, 0.025, 0.0)
            }
            if (elapsed % 6L == 0L) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, centre.x, centre.y + 0.65 + progress * 0.95, centre.z, 3, 0.42, 0.18, 0.42, 0.01)
            }
            if (elapsed % 10L == 0L) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, centre.x, centre.y + 0.65 + progress, centre.z, 4, 0.35, 0.35, 0.35, 0.015)
            }
        }
    }

    private object ParticleTypesHolder {
        val PINK = ConjureParticleOptions(0xFF5FB0)
        val RED = ConjureParticleOptions(0xE3334D)
        val WHITE = ConjureParticleOptions(0xFFF2FF)
    }
}
