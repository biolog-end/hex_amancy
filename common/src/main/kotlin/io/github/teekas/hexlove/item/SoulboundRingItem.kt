package io.github.teekas.hexlove.item

import at.petrak.hexcasting.api.item.VariantItem
import io.github.teekas.hexlove.marriage.RingData
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import java.util.Locale
import java.util.UUID

/** A ring is inert when crafted. Soulbind writes its owner, and the altar writes its partner. */
class SoulboundRingItem(properties: Properties) : Item(properties), VariantItem {
    override fun numVariants(): Int = 6

    override fun isFoil(stack: ItemStack): Boolean = false

    override fun appendHoverText(
        stack: ItemStack,
        level: Level?,
        tooltip: MutableList<Component>,
        flag: TooltipFlag,
    ) {
        val owner = RingData.boundTo(stack) ?: return
        val auraStyle = Style.EMPTY.withColor(TextColor.fromRgb(RingData.auraColor(stack)))
        tooltip += Component.translatable(
            "item.hexlove.soulbound_ring.owner",
            nameOf(level?.server, owner, RingData.boundName(stack), auraStyle),
        )
            .withStyle(ChatFormatting.LIGHT_PURPLE)
        RingData.spouse(stack)?.let { spouse ->
            tooltip += Component.translatable(
                "item.hexlove.soulbound_ring.spouse",
                nameOf(level?.server, spouse, RingData.spouseName(stack), auraStyle),
            )
                .withStyle(ChatFormatting.AQUA)
        } ?: run {
            tooltip += Component.translatable("item.hexlove.soulbound_ring.awaiting_vow")
                .withStyle(ChatFormatting.GRAY)
        }
        if (RingData.isWorldLinked(stack, owner)) {
            tooltip += Component.translatable("item.hexlove.soulbound_ring.world_linked")
                .withStyle(ChatFormatting.LIGHT_PURPLE)
            tooltip += Component.translatable(
                "item.hexlove.soulbound_ring.world_charge",
                String.format(Locale.ROOT, "%.2f", RingData.worldCharge(stack)),
                RingData.MAX_WORLD_CHARGE.toInt(),
            ).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(WORLD_AFFECTION_COLOUR)))
        }
    }

    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        // Also upgrade old rings after this update. Only the owner may author their displayed name.
        if (!level.isClientSide && entity is ServerPlayer && RingData.boundTo(stack) == entity.uuid && RingData.boundName(stack) == null) {
            RingData.rememberOwnerName(stack, entity.gameProfile.name)
        }
    }

    private fun nameOf(server: MinecraftServer?, uuid: UUID, savedName: String?, style: Style): Component {
        savedName?.let(Component::literal)?.let { return it.withStyle(style) }
        val online = server?.playerList?.getPlayer(uuid)
        if (online != null) return online.displayName.copy().withStyle(style)
        val rememberedName = server?.profileCache?.get(uuid)?.orElse(null)?.name
        return rememberedName?.let(Component::literal)?.withStyle(style)
            ?: Component.translatable("item.hexlove.soulbound_ring.unknown").withStyle(style)
    }

    private companion object {
        const val WORLD_AFFECTION_COLOUR = 0xFF5C8A
    }
}
