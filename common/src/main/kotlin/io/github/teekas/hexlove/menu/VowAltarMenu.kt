package io.github.teekas.hexlove.menu

import io.github.teekas.hexlove.block.entity.VowAltarBlockEntity
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.registry.HexloveMenus
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * The two offerings are real server-owned slots. This makes the rite understandable at a glance,
 * while the altar still verifies that only a ring's owner can insert or remove it.
 */
class VowAltarMenu private constructor(
    containerId: Int,
    private val playerInventory: Inventory,
    private val offerings: Container,
    private val altar: VowAltarBlockEntity?,
) : AbstractContainerMenu(HexloveMenus.VOW_ALTAR.value, containerId) {
    constructor(containerId: Int, playerInventory: Inventory) :
        this(containerId, playerInventory, SimpleContainer(OFFERING_SLOT_COUNT), null)

    constructor(containerId: Int, playerInventory: Inventory, altar: VowAltarBlockEntity) :
        this(containerId, playerInventory, altar, altar)

    init {
        addSlot(OfferingSlot(offerings, 0, 55, 44))
        addSlot(OfferingSlot(offerings, 1, 103, 44))

        for (row in 0 until 3) {
            for (column in 0 until 9) {
                addSlot(Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 142 + row * 18))
            }
        }
        for (column in 0 until 9) {
            addSlot(Slot(playerInventory, column, 8 + column * 18, 196))
        }
    }

    override fun stillValid(player: Player): Boolean = altar?.isUsableBy(player) ?: true

    override fun clickMenuButton(player: Player, id: Int): Boolean =
        id == SEVER_VOW && altar?.severVow(player) == true

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        if (slotId in 0 until OFFERING_SLOT_COUNT) {
            if (clickType == ClickType.QUICK_CRAFT) return
            val slot = slots[slotId]
            if (!slot.item.isEmpty && altar?.canTakeRing(player, slot.item) == false) return

            val incoming = when (clickType) {
                ClickType.SWAP -> playerInventory.getItem(button)
                else -> carried
            }
            if (!incoming.isEmpty && altar?.canPlaceRing(player, incoming) == false) return
            if (!incoming.isEmpty) altar?.notePlacement(player, slotId)
        }
        super.clicked(slotId, button, clickType, player)
    }

    override fun canDragTo(slot: Slot): Boolean = slots.indexOf(slot) !in 0 until OFFERING_SLOT_COUNT

    /** Shift-clicking an offering returns it to its owner without allowing inventory-to-altar hops. */
    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        if (index !in 0 until OFFERING_SLOT_COUNT) return ItemStack.EMPTY
        val slot = slots[index]
        if (!slot.hasItem() || altar?.canTakeRing(player, slot.item) != true) return ItemStack.EMPTY

        val original = slot.item.copy()
        if (!moveItemStackTo(slot.item, OFFERING_SLOT_COUNT, slots.size, true)) return ItemStack.EMPTY
        if (slot.item.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        return original
    }

    private class OfferingSlot(container: Container, index: Int, x: Int, y: Int) : Slot(container, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = stack.`is`(HexloveItems.SOULBOUND_RING.value)

        override fun getMaxStackSize(): Int = 1
    }

    companion object {
        const val SEVER_VOW = 1
        private const val OFFERING_SLOT_COUNT = 2
    }
}
