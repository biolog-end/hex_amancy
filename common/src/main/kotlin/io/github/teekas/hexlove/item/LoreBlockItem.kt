package io.github.teekas.hexlove.item

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block

/** BlockItems carry lore because vanilla Block itself has no item-tooltip hook. */
class LoreBlockItem(block: Block, private val loreKey: String, private val colour: Int) : BlockItem(block, Properties()) {
    override fun appendHoverText(stack: ItemStack, level: Level?, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip += Component.translatable(loreKey).withStyle(Style.EMPTY.withColor(colour).withItalic(true))
        super.appendHoverText(stack, level, tooltip, flag)
    }
}
