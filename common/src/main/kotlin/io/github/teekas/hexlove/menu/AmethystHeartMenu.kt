package io.github.teekas.hexlove.menu

import at.petrak.hexcasting.common.lib.HexItems
import io.github.teekas.hexlove.block.entity.AmethystHeartBlockEntity
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.registry.HexloveMenus
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/** One meaningful slot, one fractional value, and the player's ordinary inventory. */
class AmethystHeartMenu private constructor(
    containerId: Int,
    private val playerInventory: Inventory,
    private val dustContainer: Container,
    private val data: ContainerData,
    private val heart: AmethystHeartBlockEntity?,
) : AbstractContainerMenu(HexloveMenus.AMETHYST_HEART.value, containerId) {
    constructor(containerId: Int, playerInventory: Inventory) :
        this(
            containerId,
            playerInventory,
            SimpleContainer(HEART_SLOT_COUNT),
            SimpleContainerData(DATA_COUNT),
            null,
        )

    constructor(containerId: Int, playerInventory: Inventory, heart: AmethystHeartBlockEntity) :
        this(containerId, playerInventory, heart, HeartData(heart), heart)

    init {
        checkContainerSize(dustContainer, HEART_SLOT_COUNT)
        checkContainerDataCount(data, DATA_COUNT)
        // Slot centre must line up with the drawn reliquary/halo. The gold halo painted around the
        // frame spans (46..72, 47..69) with centre (59, 58); a 16x16 vanilla item drawn at slot
        // (x, y) has centre (x+8, y+8) — so slot=(51, 50) puts the item exactly in the middle.
        addSlot(DustSlot(dustContainer, DUST_SLOT, 51, 50, heart))

        for (row in 0 until 3) {
            for (column in 0 until 9) {
                addSlot(Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 126 + row * 18))
            }
        }
        for (column in 0 until 9) {
            addSlot(Slot(playerInventory, column, 8 + column * 18, 184))
        }
        addDataSlots(data)
    }

    val progressPermille: Int
        get() = data.get(PROGRESS_DATA).coerceIn(0, AmethystHeartBlockEntity.PROGRESS_SCALE.toInt())

    val isOverflowing: Boolean
        get() = progressPermille >= AmethystHeartBlockEntity.PROGRESS_SCALE &&
            getSlot(DUST_SLOT).item.let {
                it.`is`(HexItems.AMETHYST_DUST) && it.count >= HexItems.AMETHYST_DUST.maxStackSize
            }

    override fun stillValid(player: Player): Boolean = heart?.isUsableBy(player) ?: true

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        if (index !in slots.indices) return ItemStack.EMPTY
        val slot = slots[index]
        if (!slot.hasItem()) return ItemStack.EMPTY

        val stack = slot.item
        val original = stack.copy()
        val moved = if (index == DUST_SLOT) {
            moveItemStackTo(stack, HEART_SLOT_COUNT, slots.size, true)
        } else {
            accepts(stack) && moveItemStackTo(stack, DUST_SLOT, HEART_SLOT_COUNT, false)
        }
        if (!moved) return ItemStack.EMPTY

        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        if (stack.count == original.count) return ItemStack.EMPTY
        slot.onTake(player, stack)
        return original
    }

    private class DustSlot(
        container: Container,
        index: Int,
        x: Int,
        y: Int,
        private val heart: AmethystHeartBlockEntity?,
    ) : Slot(container, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = accepts(stack)

        override fun onTake(player: Player, stack: ItemStack) {
            super.onTake(player, stack)
            heart?.awardHarvest(player)
        }
    }

    private class HeartData(private val heart: AmethystHeartBlockEntity) : ContainerData {
        override fun get(index: Int): Int = if (index == PROGRESS_DATA) heart.progressPermille() else 0

        override fun set(index: Int, value: Int) = Unit

        override fun getCount(): Int = DATA_COUNT
    }

    companion object {
        private const val DUST_SLOT = 0
        private const val HEART_SLOT_COUNT = 1
        private const val PROGRESS_DATA = 0
        private const val DATA_COUNT = 1

        /** Dust to store, or raw chimera meat for the heart to soak and spit back out. */
        private fun accepts(stack: ItemStack): Boolean =
            stack.`is`(HexItems.AMETHYST_DUST) || stack.`is`(HexloveItems.CHIMERA_MEAT.value)
    }
}
