package io.github.teekas.hexlove.block.entity

import at.petrak.hexcasting.api.misc.MediaConstants
import at.petrak.hexcasting.common.lib.HexItems
import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import io.github.teekas.hexlove.menu.AmethystHeartMenu
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.block.AmethystHeartBlock
import io.github.teekas.hexlove.registry.HexloveBlockEntities
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.registry.HexloveSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * A one-slot crystalliser. Fractional media remains in [storedMedia]; every complete dust moves
 * into [dustStack] immediately while the slot has room. Once both the stack and the phial are full,
 * the heart has exactly sixty-five dusts of capacity and further affection is visibly wasted.
 *
 * The slot also accepts raw chimera meat instead of dust. In that mode the heart never crystallises
 * anything: each whole dust of affection is spent soaking one cut of meat, which the heart then
 * spits back out as [HexloveItems.RESONANT_MEAT].
 */
class AmethystHeartBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(HexloveBlockEntities.AMETHYST_HEART.value, pos, state), MenuProvider, Container {
    private var dustStack = ItemStack.EMPTY
    private var storedMedia = 0L
    private var hasBreedingYield = false

    private val isSoakingMeat: Boolean
        get() = dustStack.`is`(HexloveItems.CHIMERA_MEAT.value)

    /**
     * Stores as much of [media] as the heart can still hold and returns the exact accepted amount.
     * A partial return lets the breeding caller distinguish a normal harvest from an overflow.
     */
    fun receive(media: Long): Long {
        if (media <= 0L) return 0L
        crystalliseWholeDust()
        val accepted = minOf(media, remainingCapacity())
        // Anything the heart could not take is audibly refused, matching the four wasted streams.
        if (accepted < media) play(HexloveSounds.AMETHYST_HEART_OVERFLOW.value, 0.8f, 1.0f)
        if (accepted <= 0L) return 0L

        val dustBefore = dustStack.count
        storedMedia += accepted
        crystalliseWholeDust()
        sync()
        play(HexloveSounds.AMETHYST_HEART_CHARGE.value, 0.7f, 1.0f)
        if (dustStack.count > dustBefore) play(HexloveSounds.AMETHYST_HEART_CRYSTALLISE.value, 0.55f, 1.0f)
        return accepted
    }

    private fun play(sound: SoundEvent, volume: Float, pitch: Float) {
        (level as? ServerLevel)?.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch)
    }

    /** A compact, packet-safe value used by the menu to draw the fractional phial. */
    fun progressPermille(): Int {
        val visibleMedia = storedMedia.coerceIn(0L, MediaConstants.DUST_UNIT)
        return ((visibleMedia * PROGRESS_SCALE) / MediaConstants.DUST_UNIT).toInt()
    }

    fun awardHarvest(player: Player) {
        if (hasBreedingYield && player is ServerPlayer) {
            HexloveAdvancements.grant(player, HexloveAdvancements.ENERGY_OF_PASSION)
        }
    }

    fun isUsableBy(player: Player): Boolean =
        player.level() === level && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= MENU_RADIUS * MENU_RADIUS

    override fun getDisplayName(): Component = Component.translatable("block.hexlove.amethyst_heart")

    override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
        AmethystHeartMenu(containerId, inventory, this)

    override fun getContainerSize(): Int = 1

    override fun isEmpty(): Boolean = dustStack.isEmpty

    override fun getItem(slot: Int): ItemStack = if (slot == DUST_SLOT) dustStack else ItemStack.EMPTY

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        if (slot != DUST_SLOT || amount <= 0 || dustStack.isEmpty) return ItemStack.EMPTY
        val removed = dustStack.split(amount)
        if (dustStack.isEmpty) dustStack = ItemStack.EMPTY
        if (!removed.isEmpty) setChanged()
        return removed
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        if (slot != DUST_SLOT) return ItemStack.EMPTY
        val removed = dustStack
        dustStack = ItemStack.EMPTY
        crystalliseWholeDust()
        return removed
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot != DUST_SLOT || (!stack.isEmpty && !accepts(stack))) return
        dustStack = stack.copyWithCount(stack.count.coerceAtMost(maxStackSize))
        setChanged()
    }

    /**
     * Block removal must drop only the tangible slot. The fractional media is energy, not a hidden
     * second item stack, and therefore follows the old heart's behaviour when its block is broken.
     */
    fun takeDustForDrop(): ItemStack {
        val dropped = dustStack
        dustStack = ItemStack.EMPTY
        super.setChanged()
        return dropped
    }

    override fun setChanged() {
        crystalliseWholeDust()
        sync()
    }

    override fun stillValid(player: Player): Boolean = isUsableBy(player)

    override fun clearContent() {
        dustStack = ItemStack.EMPTY
        setChanged()
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        tag.putLong(MEDIA_TAG, storedMedia)
        tag.putBoolean(BREEDING_YIELD_TAG, hasBreedingYield)
        if (!dustStack.isEmpty) tag.put(DUST_TAG, dustStack.save(CompoundTag()))
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        storedMedia = tag.getLong(MEDIA_TAG).coerceAtLeast(0L)
        hasBreedingYield = tag.getBoolean(BREEDING_YIELD_TAG)
        dustStack = if (tag.contains(DUST_TAG)) ItemStack.of(tag.getCompound(DUST_TAG)) else ItemStack.EMPTY
        if (!accepts(dustStack)) dustStack = ItemStack.EMPTY
        if (!dustStack.isEmpty && dustStack.count > maxStackSize) dustStack.count = maxStackSize

        // Old saves kept every whole dust inside "media". Move that value into the new physical
        // slot without deleting any legacy surplus that may exceed the new sixty-five-dust limit.
        crystalliseWholeDust()
    }

    override fun getUpdateTag(): CompoundTag = CompoundTag().also(::saveAdditional)

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    private fun crystalliseWholeDust() {
        // Meat occupies the same slot, so there is nowhere to put a dust while it soaks.
        if (isSoakingMeat) return
        if (storedMedia < MediaConstants.DUST_UNIT) return
        val stackRoom = HexItems.AMETHYST_DUST.maxStackSize - dustStack.count
        if (stackRoom <= 0) return

        val dust = minOf(storedMedia / MediaConstants.DUST_UNIT, stackRoom.toLong()).toInt()
        if (dust <= 0) return
        if (dustStack.isEmpty) {
            dustStack = ItemStack(HexItems.AMETHYST_DUST, dust)
        } else {
            dustStack.grow(dust)
        }
        storedMedia -= dust.toLong() * MediaConstants.DUST_UNIT
        hasBreedingYield = true
    }

    private fun remainingCapacity(): Long {
        // Legacy hearts can contain more than the new phial holds. They remain drainable, but do
        // not accept more media until the player has taken the old surplus out.
        if (storedMedia > MediaConstants.DUST_UNIT) return 0L
        val phialRoom = (MediaConstants.DUST_UNIT - storedMedia).coerceAtLeast(0L)
        // While meat soaks, only the phial can fill: the moment it brims, the heart spends it.
        if (isSoakingMeat) return phialRoom
        val emptyDustSlots = (HexItems.AMETHYST_DUST.maxStackSize - dustStack.count).coerceAtLeast(0)
        return emptyDustSlots.toLong() * MediaConstants.DUST_UNIT + phialRoom
    }

    /**
     * Spends one whole dust of affection on one cut of chimera meat and spits the result out.
     * Driven from [tick] rather than from [setChanged] so a [ServerLevel] is always available for
     * the item and its effect.
     */
    private fun resonate(level: ServerLevel) {
        if (!isSoakingMeat || storedMedia < MediaConstants.DUST_UNIT) return

        storedMedia -= MediaConstants.DUST_UNIT
        dustStack.shrink(1)
        if (dustStack.isEmpty) dustStack = ItemStack.EMPTY

        spit(level, ItemStack(HexloveItems.RESONANT_MEAT.value))
        sync()
    }

    /** Throws [payload] clear of the heart's mouth and dresses the throw up as a real one. */
    private fun spit(level: ServerLevel, payload: ItemStack) {
        val front = (blockState.block as? AmethystHeartBlock)?.mouthDirection(blockState) ?: Direction.UP
        val mouth = Vec3.atCenterOf(worldPosition)
            .add(front.stepX * SPIT_REACH, SPIT_LIFT + front.stepY * SPIT_REACH, front.stepZ * SPIT_REACH)

        val item = ItemEntity(level, mouth.x, mouth.y, mouth.z, payload)
        item.setDeltaMovement(
            front.stepX * SPIT_SPEED,
            front.stepY * SPIT_SPEED + SPIT_ARC,
            front.stepZ * SPIT_SPEED,
        )
        item.setPickUpDelay(10)
        level.addFreshEntity(item)

        level.sendParticles(PINK_MOTE, mouth.x, mouth.y, mouth.z, 12, 0.16, 0.16, 0.16, 0.09)
        level.sendParticles(LILAC_MOTE, mouth.x, mouth.y, mouth.z, 6, 0.12, 0.12, 0.12, 0.07)
        level.sendParticles(SPRAY_MOTE, mouth.x, mouth.y, mouth.z, 6, 0.10, 0.10, 0.10, 0.14)
        level.playSound(null, worldPosition, HexloveSounds.AMETHYST_HEART_RESONATE.value, SoundSource.BLOCKS, 1.0f, 1.0f)
    }

    private fun sync() {
        super.setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    companion object {
        const val PROGRESS_SCALE = 1_000L
        private const val DUST_SLOT = 0
        private const val MENU_RADIUS = 6.0
        private const val MEDIA_TAG = "media"
        private const val DUST_TAG = "dust"
        private const val BREEDING_YIELD_TAG = "breeding_yield"
        private const val BEAT_PERIOD = 80L
        private const val BEAT_PERIOD_FULL = 40L
        private const val SPIT_REACH = 0.55
        private const val SPIT_LIFT = 0.12
        private const val SPIT_SPEED = 0.22
        private const val SPIT_ARC = 0.18
        private val PINK_MOTE = ConjureParticleOptions(0xFF5FB0)
        private val LILAC_MOTE = ConjureParticleOptions(0xD6A5FF)
        private val SPRAY_MOTE = ConjureParticleOptions(0xFFE6F6)

        /** The single slot holds either plain dust or the chimera meat being soaked in it. */
        private fun accepts(stack: ItemStack): Boolean =
            stack.`is`(HexItems.AMETHYST_DUST) || stack.`is`(HexloveItems.CHIMERA_MEAT.value)

        /**
         * Empty and partly filled hearts share the same quiet pulse. Only a full stack of sixty-four
         * dusts makes the passive effect bloom; a full phial may then take the total to sixty-five.
         */
        @JvmStatic
        fun tick(level: ServerLevel, pos: BlockPos, state: BlockState, heart: AmethystHeartBlockEntity) {
            heart.resonate(level)
            val saturated = heart.dustStack.`is`(HexItems.AMETHYST_DUST) &&
                heart.dustStack.count >= HexItems.AMETHYST_DUST.maxStackSize

            // A heart holding something is audibly alive; an empty one stays decoration. The
            // positional offset keeps a shelf of hearts from beating in one drum-like unison.
            if (!heart.dustStack.isEmpty || heart.storedMedia > 0L) {
                val period = if (saturated) BEAT_PERIOD_FULL else BEAT_PERIOD
                if ((level.gameTime + (pos.hashCode().toLong() and 0x3FL)) % period == 0L) {
                    level.playSound(
                        null, pos, HexloveSounds.AMETHYST_HEART_BEAT.value, SoundSource.BLOCKS,
                        if (saturated) 0.45f else 0.28f, 1.0f,
                    )
                }
            }

            if (level.gameTime % (if (saturated) 5L else 12L) != 0L) return
            level.sendParticles(
                PINK_MOTE,
                pos.x + 0.5, pos.y + 0.72, pos.z + 0.5,
                if (saturated) 4 else 1, 0.32, 0.38, 0.32, 0.022,
            )
            level.sendParticles(
                LILAC_MOTE,
                pos.x + 0.5, pos.y + 0.58, pos.z + 0.5,
                if (saturated) 2 else 1, 0.20, 0.30, 0.20, 0.014,
            )
        }
    }
}
