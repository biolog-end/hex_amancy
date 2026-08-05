package io.github.teekas.hexlove.menu

import io.github.teekas.hexlove.block.entity.WorldSoulAltarBlockEntity
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.registry.HexloveMenus
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class WorldSoulAltarMenu private constructor(
    containerId: Int,
    private val playerInventory: Inventory,
    private val altarContainer: Container,
    private val data: ContainerData,
    private val altar: WorldSoulAltarBlockEntity?,
) : AbstractContainerMenu(HexloveMenus.WORLD_SOUL_ALTAR.value, containerId) {
    constructor(containerId: Int, playerInventory: Inventory) :
        this(
            containerId,
            playerInventory,
            SimpleContainer(WorldSoulAltarBlockEntity.SLOT_COUNT),
            SimpleContainerData(DATA_COUNT),
            null,
        )

    constructor(containerId: Int, playerInventory: Inventory, altar: WorldSoulAltarBlockEntity) :
        this(containerId, playerInventory, altar, AltarData(altar), altar)

    init {
        addSlot(AmethystSlot(altarContainer, WorldSoulAltarBlockEntity.AMETHYST_SLOT, 34, 56))
        addSlot(RingSlot(altarContainer, WorldSoulAltarBlockEntity.RING_SLOT, 116, 56))
        for (row in 0 until 3) {
            for (column in 0 until 9) {
                addSlot(Slot(playerInventory, column + row * 9 + 9, 35 + column * 18, 157 + row * 18))
            }
        }
        for (column in 0 until 9) {
            addSlot(Slot(playerInventory, column, 35 + column * 18, 213))
        }
        addDataSlots(data)
    }

    val chargeMillis: Int
        get() = data.get(CHARGE_DATA).coerceIn(0, MAX_CHARGE_MILLIS)

    val activeStructure: Boolean
        get() = data.get(ACTIVE_DATA) != 0

    val structureProblem: Int
        get() = data.get(PROBLEM_DATA)

    val ritualProgress: Int
        get() = data.get(RITUAL_DATA).coerceIn(0, WorldSoulAltarBlockEntity.PROGRESS_SCALE)

    val ritualRunning: Boolean
        get() = ritualProgress > 0

    override fun stillValid(player: Player): Boolean = altar?.isUsableBy(player) ?: true

    override fun clickMenuButton(player: Player, id: Int): Boolean = when (id) {
        ATTUNE -> altar?.startAttunement(player) == true
        EMBODY -> altar?.embody(player) == true
        else -> false
    }

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        if (slotId in 0 until WorldSoulAltarBlockEntity.SLOT_COUNT) {
            if (clickType == ClickType.QUICK_CRAFT) return
            if (altar?.canTake(player, slotId) == false) return
            val incoming = if (clickType == ClickType.SWAP) playerInventory.getItem(button) else carried
            if (!incoming.isEmpty && altar?.canPlace(player, slotId, incoming) == false) return
        }
        super.clicked(slotId, button, clickType, player)
    }

    override fun canDragTo(slot: Slot): Boolean =
        slots.indexOf(slot) !in 0 until WorldSoulAltarBlockEntity.SLOT_COUNT

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        if (index !in slots.indices) return ItemStack.EMPTY
        val slot = slots[index]
        if (!slot.hasItem()) return ItemStack.EMPTY
        val original = slot.item.copy()
        val stack = slot.item
        val moved = if (index < WorldSoulAltarBlockEntity.SLOT_COUNT) {
            altar?.canTake(player, index) != false &&
                moveItemStackTo(stack, WorldSoulAltarBlockEntity.SLOT_COUNT, slots.size, true)
        } else {
            when {
                stack.`is`(Items.AMETHYST_SHARD) &&
                    altar?.canPlace(player, WorldSoulAltarBlockEntity.AMETHYST_SLOT, stack) != false ->
                    moveItemStackTo(stack, WorldSoulAltarBlockEntity.AMETHYST_SLOT, WorldSoulAltarBlockEntity.AMETHYST_SLOT + 1, false)
                stack.`is`(HexloveItems.SOULBOUND_RING.value) &&
                    altar?.canPlace(player, WorldSoulAltarBlockEntity.RING_SLOT, stack) != false ->
                    moveItemStackTo(stack, WorldSoulAltarBlockEntity.RING_SLOT, WorldSoulAltarBlockEntity.RING_SLOT + 1, false)
                else -> false
            }
        }
        if (!moved) return ItemStack.EMPTY
        if (stack.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        if (stack.count == original.count) return ItemStack.EMPTY
        slot.onTake(player, stack)
        return original
    }

    private class AmethystSlot(container: Container, index: Int, x: Int, y: Int) : Slot(container, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = stack.`is`(Items.AMETHYST_SHARD)
    }

    private class RingSlot(container: Container, index: Int, x: Int, y: Int) : Slot(container, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = stack.`is`(HexloveItems.SOULBOUND_RING.value)
        override fun getMaxStackSize(): Int = 1
    }

    private class AltarData(private val altar: WorldSoulAltarBlockEntity) : ContainerData {
        override fun get(index: Int): Int = when (index) {
            CHARGE_DATA -> altar.chargeMillis()
            ACTIVE_DATA -> if (altar.isStructureActive()) 1 else 0
            RITUAL_DATA -> (altar.ritualProgress() * WorldSoulAltarBlockEntity.PROGRESS_SCALE).toInt()
            PROBLEM_DATA -> altar.structureProblemCode()
            else -> 0
        }

        override fun set(index: Int, value: Int) = Unit

        override fun getCount(): Int = DATA_COUNT
    }

    companion object {
        const val ATTUNE = 1
        const val EMBODY = 2
        private const val CHARGE_DATA = 0
        private const val ACTIVE_DATA = 1
        private const val RITUAL_DATA = 2
        private const val PROBLEM_DATA = 3
        private const val DATA_COUNT = 4
        const val MAX_CHARGE_MILLIS = 64_000
    }
}
