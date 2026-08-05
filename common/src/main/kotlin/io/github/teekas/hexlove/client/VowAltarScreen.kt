package io.github.teekas.hexlove.client

import io.github.teekas.hexlove.menu.VowAltarMenu
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import kotlin.math.roundToInt
import kotlin.math.sin

/** A two-slot altar inventory with a hand-drawn, pixel-art rite rather than a borrowed container sheet. */
class VowAltarScreen(
    menu: VowAltarMenu,
    inventory: Inventory,
    title: Component,
) : AbstractContainerScreen<VowAltarMenu>(menu, inventory, title) {
    init {
        imageWidth = 176
        imageHeight = 216
    }

    override fun init() {
        super.init()
        addRenderableWidget(actionButton(94, "screen.hexlove.vow_altar.sever", VowAltarMenu.SEVER_VOW))
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)
    }

    private fun actionButton(yOffset: Int, key: String, action: Int): Button =
        RitualButton(
            leftPos + 16,
            topPos + yOffset,
            144,
            20,
            Component.translatable(key),
        ) {
            minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, action)
        }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos
        val ticks = (minecraft?.level?.gameTime ?: 0L) + partialTick.toDouble()
        val firstOffered = !menu.getSlot(0).item.isEmpty
        val secondOffered = !menu.getSlot(1).item.isEmpty
        val joined = firstOffered && secondOffered
        // A dark amethyst slab with a gold inlay: every piece is drawn in code so it keeps the
        // crisp Minecraft pixel style at every GUI scale.
        graphics.fill(x, y, x + imageWidth, y + imageHeight, OUTER_SHADOW)
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, FRAME)
        graphics.fillGradient(x + 5, y + 5, x + imageWidth - 5, y + imageHeight - 5, BACKGROUND_TOP, BACKGROUND_BOTTOM)
        drawCornerOrnament(graphics, 8, 8, false, false)
        drawCornerOrnament(graphics, imageWidth - 9, 8, true, false)
        drawCornerOrnament(graphics, 8, imageHeight - 9, false, true)
        drawCornerOrnament(graphics, imageWidth - 9, imageHeight - 9, true, true)

        drawPanel(graphics, 8, 22, 160, 68, RITE_PANEL, RITE_BORDER)
        drawPanel(graphics, 23, 28, 130, 14, INSCRIPTION_PANEL, INSCRIPTION_BORDER)
        drawFallingPetals(graphics, ticks, firstOffered || secondOffered)
        drawOathThread(graphics, ticks, joined)
        drawSlotFrame(graphics, 55, 44, ticks, firstOffered, false)
        drawSlotFrame(graphics, 103, 44, ticks, secondOffered, true)

        drawPanel(graphics, 14, 117, 148, 10, WARNING_PANEL, WARNING_BORDER)
        drawPanel(graphics, 8, 130, 160, 84, INVENTORY_PANEL, INVENTORY_BORDER)
        drawInventoryDetails(graphics)
        drawInventorySlots(graphics, mouseX, mouseY)
        drawInventoryTab(graphics)
    }

    /** Empty slots need their own inset frame: unlike a furnace, this screen has no vanilla texture sheet. */
    private fun drawSlotFrame(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        ticks: Double,
        offered: Boolean,
        mirrored: Boolean,
    ) {
        val left = leftPos + x
        val top = topPos + y
        if (offered) {
            val pulse = ((sin(ticks * 0.11 + if (mirrored) 1.2 else 0.0) + 1.0) * 0.5)
            val glow = withAlpha(SLOT_OFFERING_GLOW, 18 + (pulse * 34).toInt())
            graphics.fill(left - 5, top - 3, left + 23, top + 21, glow)
            graphics.fill(left - 3, top - 5, left + 21, top + 23, glow)
        }
        graphics.fill(left - 2, top - 2, left + 20, top + 20, SLOT_SHADOW)
        graphics.fill(left - 1, top - 1, left + 19, top + 19, SLOT_GOLD)
        graphics.fill(left, top, left + 18, top + 18, SLOT_LILAC)
        graphics.fill(left + 2, top + 2, left + 16, top + 16, SLOT_INSET)
        graphics.fill(left + 3, top + 3, left + 15, top + 15, SLOT_WELL)
        graphics.fill(left, top, left + 18, top + 1, 0xBFFFF2D1.toInt())
        graphics.fill(left, top, left + 1, top + 18, 0x80FFF1D0.toInt())
        // Four tiny prongs turn the square slot into an authored ring setting.
        val prong = if (offered) SLOT_PRONG_LIT else SLOT_PRONG
        pixel(graphics, left - 3, top + 8, prong)
        pixel(graphics, left + 20, top + 8, prong)
        pixel(graphics, left + 8, top - 3, prong)
        pixel(graphics, left + 8, top + 20, prong)
    }

    private fun drawPanel(graphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, fill: Int, border: Int) {
        val left = leftPos + x
        val top = topPos + y
        graphics.fill(left + 2, top, left + width - 2, top + height, fill)
        graphics.fill(left, top + 2, left + width, top + height - 2, fill)
        graphics.fill(left + 2, top, left + width - 2, top + 1, border)
        graphics.fill(left + 2, top + height - 1, left + width - 2, top + height, 0xC97F5A91.toInt())
        graphics.fill(left, top + 2, left + 1, top + height - 2, border)
        graphics.fill(left + width - 1, top + 2, left + width, top + height - 2, 0xC97F5A91.toInt())
        graphics.fill(left + 3, top + 3, left + width - 3, top + 4, 0x3BFFF1D0)
    }

    /** Two silk-coloured strands visibly braid into one vow instead of becoming cosmic energy. */
    private fun drawOathThread(graphics: GuiGraphics, ticks: Double, joined: Boolean) {
        val y = topPos + 53
        graphics.fill(leftPos + 74, y - 1, leftPos + 102, y + 2, THREAD_SHADOW)
        for (offset in 0..28) {
            val px = leftPos + 74 + offset
            val phase = if (joined) ticks * 0.20 else 0.0
            val wave = sin(offset * 0.72 + phase)
            val firstY = y + wave.roundToInt()
            val secondY = y - wave.roundToInt()
            pixel(graphics, px, firstY, if (joined) THREAD_ROSE else THREAD_DIM)
            pixel(graphics, px, secondY, if (joined) THREAD_IVORY else THREAD_BRIGHT)
        }
        val knotX = leftPos + 88
        val knotPulse = if (joined) ((sin(ticks * 0.15) + 1.0) * 0.5) else 0.15
        graphics.fill(knotX - 3, y - 3, knotX + 4, y + 4, withAlpha(KNOT_GLOW, 24 + (knotPulse * 46).toInt()))
        graphics.fill(knotX - 1, y - 3, knotX + 2, y + 4, KNOT_GOLD)
        graphics.fill(knotX - 3, y - 1, knotX + 4, y + 2, KNOT_GOLD)
        graphics.fill(knotX, y - 1, knotX + 1, y + 2, if (joined) KNOT_WHITE else KNOT_CORE)
    }

    /** A quiet recessed drawer stays behind the real Minecraft slots instead of competing with them. */
    private fun drawInventoryDetails(graphics: GuiGraphics) {
        graphics.fillGradient(
            leftPos + 10,
            topPos + 141,
            leftPos + 166,
            topPos + 212,
            INVENTORY_DRAWER_TOP,
            INVENTORY_DRAWER_BOTTOM,
        )
        graphics.fill(leftPos + 12, topPos + 141, leftPos + 164, topPos + 142, INVENTORY_DRAWER_RIM)
        graphics.fill(leftPos + 12, topPos + 211, leftPos + 164, topPos + 212, INVENTORY_DRAWER_RIM)
    }

    /** A handful of slow rose petals gives the rite motion without turning it into a star field. */
    private fun drawFallingPetals(graphics: GuiGraphics, ticks: Double, awake: Boolean) {
        if (!awake) return
        for (index in 0 until 5) {
            val travel = (ticks * (0.17 + index * 0.012) + index * 13.0) % 38.0
            val petalY = topPos + 45 + travel.toInt()
            val sway = (sin(ticks * 0.09 + index * 1.8) * 4.0).roundToInt()
            val petalX = leftPos + 25 + index * 29 + sway
            val colour = if (index % 2 == 0) PETAL_ROSE else PETAL_IVORY
            graphics.fill(petalX, petalY, petalX + 2, petalY + 1, colour)
            pixel(graphics, petalX + if (index % 2 == 0) 1 else 0, petalY + 1, withAlpha(colour, 175))
        }
    }

    /** The inventory label is a section tab, not a loose line of text caught between controls. */
    private fun drawInventoryTab(graphics: GuiGraphics) {
        drawPanel(graphics, 46, 132, 84, 10, INVENTORY_TAB, INVENTORY_TAB_BORDER)
        val y = topPos + 137
        graphics.fill(leftPos + 14, y, leftPos + 42, y + 1, INVENTORY_RULE)
        graphics.fill(leftPos + 134, y, leftPos + 162, y + 1, INVENTORY_RULE)
    }

    private fun drawInventorySlots(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        for (row in 0 until 3) {
            for (column in 0 until 9) {
                drawInventorySlot(graphics, 8 + column * 18, 142 + row * 18, mouseX, mouseY, false)
            }
        }
        for (column in 0 until 9) {
            drawInventorySlot(graphics, 8 + column * 18, 196, mouseX, mouseY, true)
        }
    }

    private fun drawInventorySlot(
        graphics: GuiGraphics,
        slotX: Int,
        slotY: Int,
        mouseX: Int,
        mouseY: Int,
        hotbar: Boolean,
    ) {
        val x = leftPos + slotX
        val y = topPos + slotY
        val hovered = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16
        graphics.fill(x - 1, y - 1, x + 17, y + 17, INVENTORY_SLOT_SHADOW)
        graphics.fill(x, y, x + 16, y + 16, if (hovered) INVENTORY_SLOT_HOVER else INVENTORY_SLOT_WELL)
        graphics.fill(x, y, x + 16, y + 1, if (hotbar) INVENTORY_SLOT_GOLD else INVENTORY_SLOT_RIM)
        graphics.fill(x, y, x + 1, y + 16, INVENTORY_SLOT_RIM)
        if (hovered) graphics.fill(x + 1, y + 1, x + 15, y + 2, INVENTORY_SLOT_HOVER_GLINT)
    }

    private fun drawCornerOrnament(graphics: GuiGraphics, x: Int, y: Int, mirrorX: Boolean, mirrorY: Boolean) {
        val sx = if (mirrorX) -1 else 1
        val sy = if (mirrorY) -1 else 1
        val originX = leftPos + x
        val originY = topPos + y
        fillRelative(graphics, originX, originY, originX + sx * 5, originY + sy, ORNAMENT)
        fillRelative(graphics, originX, originY, originX + sx, originY + sy * 5, ORNAMENT)
        fillRelative(graphics, originX + sx * 3, originY + sy * 3, originX + sx * 5, originY + sy * 5, ORNAMENT_DIM)
    }

    private fun fillRelative(graphics: GuiGraphics, x1: Int, y1: Int, x2: Int, y2: Int, colour: Int) {
        graphics.fill(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2) + 1, maxOf(y1, y2) + 1, colour)
    }

    private fun pixel(graphics: GuiGraphics, x: Int, y: Int, colour: Int) =
        graphics.fill(x, y, x + 1, y + 1, colour)

    private fun withAlpha(colour: Int, alpha: Int): Int =
        (colour and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        drawCenteredLabel(graphics, title, 88, 10, TITLE)
        drawCenteredLabel(graphics, Component.translatable("screen.hexlove.vow_altar.hint"), 88, 31, TEXT)
        drawCenteredLabel(graphics, Component.translatable("screen.hexlove.vow_altar.first_slot"), 64, 70)
        drawCenteredLabel(graphics, Component.translatable("screen.hexlove.vow_altar.second_slot"), 112, 70)
        drawCenteredLabel(graphics, Component.translatable("screen.hexlove.vow_altar.sever_hint"), 88, 118, WARNING)
        drawCenteredLabel(graphics, Component.translatable("container.inventory"), 88, 133, INVENTORY_TEXT)
    }

    private fun drawCenteredLabel(graphics: GuiGraphics, text: Component, centerX: Int, y: Int, colour: Int = SLOT_TEXT) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, colour, false)
    }

    private class RitualButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        message: Component,
        onPress: OnPress,
    ) : Button(x, y, width, height, message, onPress, DEFAULT_NARRATION) {
        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val hovered = isHoveredOrFocused
            val border = if (hovered) BUTTON_HOVER_BORDER else BUTTON_BORDER
            val fill = if (hovered) BUTTON_HOVER_FILL else BUTTON_FILL
            graphics.fill(x, y, x + width, y + height, BUTTON_SHADOW)
            graphics.fill(x + 1, y, x + width - 1, y + height - 1, border)
            graphics.fillGradient(x + 2, y + 2, x + width - 2, y + height - 2, fill, BUTTON_BOTTOM)
            graphics.fill(x + 4, y + 3, x + width - 4, y + 4, BUTTON_HIGHLIGHT)
            if (hovered) {
                val ticks = (Minecraft.getInstance().level?.gameTime ?: 0L) + partialTick.toDouble()
                val shimmer = x + 5 + ((ticks * 1.7).toInt() % (width - 12))
                graphics.fill(shimmer, y + 4, shimmer + 1, y + height - 4, BUTTON_SHIMMER)
            }
            graphics.drawCenteredString(
                Minecraft.getInstance().font,
                message,
                x + width / 2,
                y + (height - 8) / 2,
                if (hovered) BUTTON_TEXT_HOVER else BUTTON_TEXT,
            )
        }
    }

    private companion object {
        const val OUTER_SHADOW = 0xF3090610.toInt()
        const val FRAME = 0xFF5A315E.toInt()
        const val BACKGROUND_TOP = 0xF82A123B.toInt()
        const val BACKGROUND_BOTTOM = 0xF8150A24.toInt()
        const val RITE_PANEL = 0xDE1B0B2A.toInt()
        const val RITE_BORDER = 0xFFB982A4.toInt()
        const val INSCRIPTION_PANEL = 0xE7180B26.toInt()
        const val INSCRIPTION_BORDER = 0xFF75496F.toInt()
        const val WARNING_PANEL = 0xE3331028.toInt()
        const val WARNING_BORDER = 0xFFC06E93.toInt()
        const val INVENTORY_PANEL = 0xC611091E.toInt()
        const val INVENTORY_BORDER = 0xFF68426D.toInt()
        const val INVENTORY_TAB = 0xFF2B1133.toInt()
        const val INVENTORY_TAB_BORDER = 0xFF9A6C98.toInt()
        const val INVENTORY_RULE = 0xFF79516F.toInt()
        const val INVENTORY_DRAWER_TOP = 0xFF1C0B29.toInt()
        const val INVENTORY_DRAWER_BOTTOM = 0xFF0B0612.toInt()
        const val INVENTORY_DRAWER_RIM = 0xFF674466.toInt()
        const val INVENTORY_SLOT_SHADOW = 0xFF09040D.toInt()
        const val INVENTORY_SLOT_WELL = 0xFF16091E.toInt()
        const val INVENTORY_SLOT_HOVER = 0xFF32162D.toInt()
        const val INVENTORY_SLOT_RIM = 0xFF56364E.toInt()
        const val INVENTORY_SLOT_GOLD = 0xFF9C6F49.toInt()
        const val INVENTORY_SLOT_HOVER_GLINT = 0x90FFD2E0.toInt()
        const val SLOT_SHADOW = 0xFF09050D.toInt()
        const val SLOT_GOLD = 0xFFE0AD72.toInt()
        const val SLOT_LILAC = 0xFF9F7BAE.toInt()
        const val SLOT_INSET = 0xFF37213F.toInt()
        const val SLOT_WELL = 0xFF130919.toInt()
        const val THREAD_DIM = 0xA45F3C68.toInt()
        const val THREAD_BRIGHT = 0xFFD5A5BD.toInt()
        const val THREAD_SHADOW = 0x98100618.toInt()
        const val THREAD_ROSE = 0xFFFF7CA9.toInt()
        const val THREAD_IVORY = 0xFFFFE3BD.toInt()
        const val KNOT_GLOW = 0xFFBE547E.toInt()
        const val KNOT_GOLD = 0xFFEFBA68.toInt()
        const val KNOT_CORE = 0xFFFFF1C9.toInt()
        const val KNOT_WHITE = 0xFFFFFFFF.toInt()
        const val SLOT_OFFERING_GLOW = 0xFFE86B9B.toInt()
        const val SLOT_PRONG = 0xFFB47D77.toInt()
        const val SLOT_PRONG_LIT = 0xFFFFD890.toInt()
        const val PETAL_ROSE = 0xFFE96691.toInt()
        const val PETAL_IVORY = 0xFFF2C9B0.toInt()
        const val ORNAMENT = 0xFFC8949F.toInt()
        const val ORNAMENT_DIM = 0x885D3963.toInt()
        const val TITLE = 0xFFFFE8F7.toInt()
        const val TEXT = 0xFFE8C9E6.toInt()
        const val TEXT_DIM = 0xFFB893BF.toInt()
        const val SLOT_TEXT = 0xFFFFD9EA.toInt()
        const val WARNING = 0xFFF3A9C8.toInt()
        const val INVENTORY_TEXT = 0xFFE7CFE6.toInt()
        const val BUTTON_SHADOW = 0xFF0B0510.toInt()
        const val BUTTON_BORDER = 0xFFA56C87.toInt()
        const val BUTTON_HOVER_BORDER = 0xFFFFD38A.toInt()
        const val BUTTON_FILL = 0xFF54213F.toInt()
        const val BUTTON_HOVER_FILL = 0xFF76314A.toInt()
        const val BUTTON_BOTTOM = 0xFF321126.toInt()
        const val BUTTON_HIGHLIGHT = 0x66FFD4E2
        const val BUTTON_SHIMMER = 0x66FFF4D5
        const val BUTTON_TEXT = 0xFFF4DCEB.toInt()
        const val BUTTON_TEXT_HOVER = 0xFFFFF2CC.toInt()
    }
}
