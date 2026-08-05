package io.github.teekas.hexlove.client

import io.github.teekas.hexlove.block.entity.AmethystHeartBlockEntity
import io.github.teekas.hexlove.menu.AmethystHeartMenu
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import kotlin.math.ceil
import kotlin.math.sin

/**
 * A hand-pixelled reliquary: the one physical dust slot is enthroned inside an amethyst heart,
 * while the narrow phial on the right shows the exact fraction of the next crystal.
 *
 * No large GUI texture is involved. Every line sits on the Minecraft pixel grid, so the ornament
 * stays crisp at all GUI scales and resource packs do not have to carry a wasteful bitmap.
 */
class AmethystHeartScreen(
    menu: AmethystHeartMenu,
    inventory: Inventory,
    title: Component,
) : AbstractContainerScreen<AmethystHeartMenu>(menu, inventory, title) {
    init {
        imageWidth = 176
        imageHeight = 204
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)

        if (inside(mouseX, mouseY, PHIAL_HOVER_X, PHIAL_HOVER_Y, PHIAL_HOVER_WIDTH, PHIAL_HOVER_HEIGHT)) {
            val key = if (menu.isOverflowing) {
                "screen.hexlove.amethyst_heart.overflow_tooltip"
            } else {
                "screen.hexlove.amethyst_heart.progress_tooltip"
            }
            val percent = (menu.progressPermille + 5) / 10
            graphics.renderTooltip(font, Component.translatable(key, percent), mouseX, mouseY)
        } else if (
            menu.getSlot(0).item.isEmpty &&
            inside(mouseX, mouseY, DUST_SLOT_X, DUST_SLOT_Y, 18, 18)
        ) {
            graphics.renderTooltip(
                font,
                Component.translatable("screen.hexlove.amethyst_heart.slot_tooltip"),
                mouseX,
                mouseY,
            )
        }
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos
        val y = topPos
        val ticks = (minecraft?.level?.gameTime ?: 0L) + partialTick.toDouble()
        val resonating = menu.progressPermille > 0 || !menu.getSlot(0).item.isEmpty

        // Deep carved amethyst, edged like a tiny gilded reliquary.
        graphics.fill(x, y, x + imageWidth, y + imageHeight, OUTER_SHADOW)
        graphics.fill(x + 2, y + 1, x + imageWidth - 2, y + imageHeight - 1, FRAME_DARK)
        graphics.fill(x + 3, y + 2, x + imageWidth - 3, y + imageHeight - 2, FRAME_GOLD)
        graphics.fillGradient(
            x + 5,
            y + 4,
            x + imageWidth - 5,
            y + imageHeight - 4,
            BACKGROUND_TOP,
            BACKGROUND_BOTTOM,
        )
        graphics.fill(x + 7, y + 20, x + imageWidth - 7, y + 21, GOLD_DIM)
        graphics.fill(x + 12, y + 21, x + imageWidth - 12, y + 22, AMETHYST_RIM)

        drawCorner(graphics, 8, 8, false, false)
        drawCorner(graphics, imageWidth - 9, 8, true, false)
        drawCorner(graphics, 8, imageHeight - 9, false, true)
        drawCorner(graphics, imageWidth - 9, imageHeight - 9, true, true)

        drawPanel(graphics, 8, 24, 160, 84, RITE_PANEL, RITE_BORDER)
        drawDustReliquary(graphics, ticks, resonating)
        drawPhial(graphics, ticks)

        // A narrow carved divider gives the compact upper illustration a deliberate lower edge.
        graphics.fill(x + 16, y + 109, x + 160, y + 110, DIVIDER_SHADOW)
        graphics.fill(x + 25, y + 108, x + 151, y + 109, DIVIDER_GOLD)
        drawDiamond(graphics, 88, 109, DIVIDER_BRIGHT)

        drawPanel(graphics, 7, 116, 162, 86, INVENTORY_PANEL, INVENTORY_BORDER)
        graphics.fillGradient(
            x + 9,
            y + 125,
            x + 167,
            y + 200,
            INVENTORY_DRAWER_TOP,
            INVENTORY_DRAWER_BOTTOM,
        )
        graphics.fill(x + 11, y + 125, x + 165, y + 126, INVENTORY_RIM)
        graphics.fill(x + 11, y + 199, x + 165, y + 200, INVENTORY_RIM_DARK)
        drawInventorySlots(graphics, mouseX, mouseY)
        drawInventoryTab(graphics)
    }

    /**
     * The slot is surrounded by a faceted heart, a double halo, curled gold vines, and eight small
     * leaves. They are asymmetric by a pixel in places on purpose: it reads as engraved, not vector.
     */
    private fun drawDustReliquary(graphics: GuiGraphics, ticks: Double, resonating: Boolean) {
        val x = leftPos
        val y = topPos

        // The heart breathes like a cut gemstone catching light, never like the cosmic World Soul.
        val pulse = ((sin(ticks * 0.105) + 1.0) * 0.5)
        val auraAlpha = if (resonating) 24 + (pulse * 34).toInt() else 9 + (pulse * 8).toInt()
        val aura = withAlpha(HEART_AURA, auraAlpha)
        graphics.fill(x + 35, y + 34, x + 82, y + 37, aura)
        graphics.fill(x + 30, y + 39, x + 87, y + 53, aura)
        graphics.fill(x + 34, y + 52, x + 83, y + 63, aura)
        graphics.fill(x + 40, y + 62, x + 77, y + 70, aura)
        graphics.fill(x + 48, y + 69, x + 69, y + 76, aura)

        // Shadowed heart silhouette behind the physical Minecraft slot.
        graphics.fill(x + 37, y + 35, x + 49, y + 38, HEART_SHADOW)
        graphics.fill(x + 68, y + 35, x + 80, y + 38, HEART_SHADOW)
        graphics.fill(x + 32, y + 39, x + 53, y + 48, HEART_SHADOW)
        graphics.fill(x + 64, y + 39, x + 85, y + 48, HEART_SHADOW)
        graphics.fill(x + 35, y + 47, x + 82, y + 61, HEART_SHADOW)
        graphics.fill(x + 40, y + 59, x + 77, y + 68, HEART_SHADOW)
        graphics.fill(x + 47, y + 67, x + 70, y + 75, HEART_SHADOW)
        graphics.fill(x + 54, y + 74, x + 63, y + 80, HEART_SHADOW)

        // Facet borders and two differently coloured lobes.
        graphics.fill(x + 36, y + 37, x + 51, y + 39, HEART_GOLD)
        graphics.fill(x + 66, y + 37, x + 81, y + 39, HEART_GOLD)
        graphics.fill(x + 33, y + 41, x + 35, y + 49, HEART_GOLD)
        graphics.fill(x + 82, y + 41, x + 84, y + 49, HEART_GOLD)
        graphics.fill(x + 37, y + 39, x + 50, y + 42, HEART_LEFT)
        graphics.fill(x + 67, y + 39, x + 80, y + 42, HEART_RIGHT)
        graphics.fill(x + 35, y + 43, x + 50, y + 51, HEART_LEFT)
        graphics.fill(x + 67, y + 43, x + 82, y + 51, HEART_RIGHT)
        graphics.fill(x + 38, y + 50, x + 79, y + 61, HEART_MIDDLE)
        graphics.fill(x + 42, y + 60, x + 75, y + 68, HEART_LOW)
        graphics.fill(x + 49, y + 68, x + 68, y + 74, HEART_LOW)
        graphics.fill(x + 55, y + 74, x + 62, y + 78, HEART_TIP)
        graphics.fill(x + 37, y + 42, x + 39, y + 48, HEART_GLINT)
        graphics.fill(x + 40, y + 40, x + 47, y + 42, HEART_GLINT)

        // Inner gold halo remains visible around an inserted item's ordinary 16x16 sprite.
        drawSlotFrame(graphics, DUST_SLOT_X, DUST_SLOT_Y)
        graphics.fill(x + 46, y + 49, x + 48, y + 67, HALO_GOLD)
        graphics.fill(x + 70, y + 49, x + 72, y + 67, HALO_GOLD)
        graphics.fill(x + 48, y + 47, x + 70, y + 49, HALO_GOLD)
        graphics.fill(x + 48, y + 67, x + 70, y + 69, HALO_GOLD)

        // Fine curled vines: two mirrored stems, each ending in small acanthus leaves.
        drawVine(graphics, false)
        drawVine(graphics, true)
        drawResonanceFacets(graphics, ticks, resonating)
    }

    private fun drawVine(graphics: GuiGraphics, mirror: Boolean) {
        val sx = if (mirror) -1 else 1
        val anchorX = leftPos + if (mirror) 88 else 29
        val y = topPos
        line(graphics, anchorX, y + 51, anchorX + sx * 8, y + 51, VINE_GOLD)
        line(graphics, anchorX + sx * 7, y + 44, anchorX + sx * 8, y + 52, VINE_GOLD)
        line(graphics, anchorX + sx * 7, y + 43, anchorX + sx * 14, y + 43, VINE_GOLD)
        line(graphics, anchorX + sx * 13, y + 38, anchorX + sx * 14, y + 44, VINE_GOLD)
        line(graphics, anchorX + sx * 8, y + 66, anchorX, y + 66, VINE_GOLD_DIM)
        line(graphics, anchorX + sx * 8, y + 65, anchorX + sx * 9, y + 73, VINE_GOLD_DIM)
        line(graphics, anchorX + sx * 9, y + 72, anchorX + sx * 16, y + 72, VINE_GOLD_DIM)

        leaf(graphics, anchorX + sx * 12, y + 38, sx, -1, LEAF_ROSE)
        leaf(graphics, anchorX + sx * 10, y + 47, sx, 1, LEAF_AMETHYST)
        leaf(graphics, anchorX + sx * 12, y + 67, sx, -1, LEAF_ROSE)
        leaf(graphics, anchorX + sx * 16, y + 75, sx, 1, LEAF_AMETHYST)
    }

    private fun leaf(graphics: GuiGraphics, x: Int, y: Int, sx: Int, sy: Int, colour: Int) {
        pixel(graphics, x, y, VINE_GOLD)
        rectRelative(graphics, x + sx, y + sy, x + sx * 3, y + sy * 2, colour)
        rectRelative(graphics, x + sx * 2, y + sy * 2, x + sx * 4, y + sy * 3, colour)
        pixel(graphics, x + sx * 3, y + sy, LEAF_GLINT)
    }

    private fun drawResonanceFacets(graphics: GuiGraphics, ticks: Double, resonating: Boolean) {
        val travellingLight = (ticks / if (resonating) 3.5 else 7.0).toInt()
        for (offset in FACET_POINTS.indices step 2) {
            val px = FACET_POINTS[offset]
            val py = FACET_POINTS[offset + 1]
            val index = offset / 2
            val lit = (travellingLight + index * 2) % 11 == 0
            val colour = when {
                lit && resonating -> FACET_WHITE
                index % 2 == 0 -> FACET_GOLD
                else -> FACET_LILAC
            }
            pixel(graphics, leftPos + px, topPos + py, colour)
            if (lit) {
                pixel(graphics, leftPos + px - 1, topPos + py, withAlpha(colour, 150))
                pixel(graphics, leftPos + px + 1, topPos + py, withAlpha(colour, 150))
            } else if (index % 4 == 0) {
                pixel(graphics, leftPos + px + 1, topPos + py + 1, colour)
            }
        }
    }

    /** A gilded glass vessel with a granular, bottom-up amethyst fill. */
    private fun drawPhial(graphics: GuiGraphics, ticks: Double) {
        val x = leftPos
        val y = topPos
        val gx = x + PHIAL_X
        val gy = y + PHIAL_Y

        // Stopper, neck, square shoulders, and the gold-acanthus outer braces.
        graphics.fill(gx + 2, gy - 9, gx + PHIAL_WIDTH - 2, gy - 7, PHIAL_SHADOW)
        graphics.fill(gx + 1, gy - 8, gx + PHIAL_WIDTH - 1, gy - 5, GOLD_DARK)
        graphics.fill(gx + 3, gy - 7, gx + PHIAL_WIDTH - 3, gy - 4, GOLD_BRIGHT)
        graphics.fill(gx + 4, gy - 4, gx + PHIAL_WIDTH - 4, gy, GLASS_EDGE)
        graphics.fill(gx - 3, gy, gx + PHIAL_WIDTH + 3, gy + 2, GOLD_DARK)
        graphics.fill(gx - 2, gy + 1, gx + PHIAL_WIDTH + 2, gy + 3, GOLD_BRIGHT)

        graphics.fill(gx - 3, gy + 2, gx + PHIAL_WIDTH + 3, gy + PHIAL_HEIGHT + 3, PHIAL_SHADOW)
        graphics.fill(gx - 2, gy + 2, gx, gy + PHIAL_HEIGHT + 2, GOLD_DARK)
        graphics.fill(gx + PHIAL_WIDTH, gy + 2, gx + PHIAL_WIDTH + 2, gy + PHIAL_HEIGHT + 2, GOLD_DARK)
        graphics.fill(gx, gy + PHIAL_HEIGHT, gx + PHIAL_WIDTH, gy + PHIAL_HEIGHT + 2, GOLD_DARK)
        graphics.fill(gx - 1, gy + 3, gx, gy + PHIAL_HEIGHT, GOLD_BRIGHT)
        graphics.fill(gx + PHIAL_WIDTH, gy + 3, gx + PHIAL_WIDTH + 1, gy + PHIAL_HEIGHT, GOLD_BRIGHT)
        graphics.fill(gx, gy + PHIAL_HEIGHT - 1, gx + PHIAL_WIDTH, gy + PHIAL_HEIGHT, GOLD_BRIGHT)

        graphics.fill(gx, gy + 2, gx + PHIAL_WIDTH, gy + PHIAL_HEIGHT - 1, GLASS_EDGE)
        graphics.fillGradient(
            gx + 1,
            gy + 3,
            gx + PHIAL_WIDTH - 1,
            gy + PHIAL_HEIGHT - 2,
            GLASS_EMPTY_TOP,
            GLASS_EMPTY_BOTTOM,
        )

        val innerHeight = PHIAL_HEIGHT - 6
        val fillHeight = if (menu.progressPermille <= 0) {
            0
        } else {
            ceil(innerHeight * menu.progressPermille / AmethystHeartBlockEntity.PROGRESS_SCALE.toDouble())
                .toInt()
                .coerceIn(1, innerHeight)
        }
        if (fillHeight > 0) {
            val bottom = gy + PHIAL_HEIGHT - 3
            val top = bottom - fillHeight
            graphics.fillGradient(gx + 2, top, gx + PHIAL_WIDTH - 2, bottom, DUST_LIGHT, DUST_DARK)
            graphics.fill(gx + 2, top, gx + PHIAL_WIDTH - 2, top + 1, DUST_RIM)
            val grainTravel = (ticks / 5.0).toInt()
            for (row in 0 until fillHeight step 4) {
                val sparkleX = gx + 3 + ((row / 4 * 5 + grainTravel) % (PHIAL_WIDTH - 6))
                pixel(graphics, sparkleX, bottom - row - 1, if (row % 8 == 0) DUST_SPARK else DUST_MID)
            }
            if (fillHeight >= 5) {
                val rise = (ticks / 2.8).toInt() % (fillHeight - 2)
                val moteY = bottom - 2 - rise
                val moteX = gx + 4 + ((ticks / 7.0).toInt() % 4)
                pixel(graphics, moteX, moteY, DUST_SPARK)
            }
        }
        graphics.fill(gx + 2, gy + 4, gx + 3, gy + PHIAL_HEIGHT - 4, GLASS_GLINT)
        pixel(graphics, gx + 3, gy + 5, GLASS_GLINT_BRIGHT)

        drawPhialAcanthus(graphics, gx, gy)
        if (menu.isOverflowing) drawOverflowCrown(graphics, gx, gy, ticks)
    }

    private fun drawPhialAcanthus(graphics: GuiGraphics, gx: Int, gy: Int) {
        line(graphics, gx - 5, gy + 10, gx - 3, gy + 10, GOLD_DIM)
        line(graphics, gx - 6, gy + 10, gx - 6, gy + 22, GOLD_DIM)
        leaf(graphics, gx - 6, gy + 15, -1, -1, LEAF_AMETHYST)
        leaf(graphics, gx - 6, gy + 22, -1, 1, LEAF_ROSE)

        line(graphics, gx + PHIAL_WIDTH + 3, gy + 10, gx + PHIAL_WIDTH + 5, gy + 10, GOLD_DIM)
        line(graphics, gx + PHIAL_WIDTH + 5, gy + 10, gx + PHIAL_WIDTH + 5, gy + 22, GOLD_DIM)
        leaf(graphics, gx + PHIAL_WIDTH + 5, gy + 15, 1, -1, LEAF_AMETHYST)
        leaf(graphics, gx + PHIAL_WIDTH + 5, gy + 22, 1, 1, LEAF_ROSE)
    }

    /** A four-colour crown echoes the four reversed particle streams in the world. */
    private fun drawOverflowCrown(graphics: GuiGraphics, gx: Int, gy: Int, ticks: Double) {
        val colours = intArrayOf(OVERFLOW_PINK, OVERFLOW_YELLOW, OVERFLOW_WHITE, OVERFLOW_RED)
        val active = (ticks / 3.0).toInt() % colours.size
        for (index in colours.indices) {
            val px = gx - 2 + index * 5
            pixel(graphics, px, gy - 12 - index % 2, colours[index])
            pixel(graphics, px + 1, gy - 12 - index % 2, colours[index])
            pixel(graphics, px, gy - 13 - index % 2, colours[index])
            if (index == active) pixel(graphics, px + 1, gy - 14 - index % 2, OVERFLOW_WHITE)
        }
    }

    private fun drawSlotFrame(graphics: GuiGraphics, slotX: Int, slotY: Int) {
        val x = leftPos + slotX
        val y = topPos + slotY
        graphics.fill(x - 2, y - 2, x + 20, y + 20, SLOT_SHADOW)
        graphics.fill(x - 1, y - 1, x + 19, y + 19, SLOT_GOLD)
        graphics.fill(x, y, x + 18, y + 18, SLOT_LILAC)
        graphics.fill(x + 2, y + 2, x + 16, y + 16, SLOT_INSET)
        graphics.fill(x + 3, y + 3, x + 15, y + 15, SLOT_WELL)
        graphics.fill(x, y, x + 18, y + 1, SLOT_GLINT)
        graphics.fill(x, y, x + 1, y + 18, SLOT_GLINT_DIM)
    }

    private fun drawPanel(graphics: GuiGraphics, px: Int, py: Int, width: Int, height: Int, fill: Int, border: Int) {
        val x = leftPos + px
        val y = topPos + py
        graphics.fill(x + 2, y, x + width - 2, y + height, fill)
        graphics.fill(x, y + 2, x + width, y + height - 2, fill)
        graphics.fill(x + 2, y, x + width - 2, y + 1, border)
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, PANEL_BOTTOM)
        graphics.fill(x, y + 2, x + 1, y + height - 2, border)
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, PANEL_RIGHT)
        graphics.fill(x + 3, y + 3, x + width - 3, y + 4, PANEL_GLINT)
    }

    private fun drawInventoryTab(graphics: GuiGraphics) {
        drawPanel(graphics, 46, 113, 84, 12, INVENTORY_TAB, INVENTORY_TAB_BORDER)
        graphics.fill(leftPos + 14, topPos + 119, leftPos + 42, topPos + 120, INVENTORY_RULE)
        graphics.fill(leftPos + 134, topPos + 119, leftPos + 162, topPos + 120, INVENTORY_RULE)
    }

    /** Quiet amethyst bezels make the carried inventory part of this reliquary without stealing focus. */
    private fun drawInventorySlots(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        for (row in 0 until 3) {
            for (column in 0 until 9) {
                drawInventorySlot(graphics, 8 + column * 18, 126 + row * 18, mouseX, mouseY, false)
            }
        }
        for (column in 0 until 9) {
            drawInventorySlot(graphics, 8 + column * 18, 184, mouseX, mouseY, true)
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
        if (hovered) {
            graphics.fill(x + 1, y + 1, x + 15, y + 2, INVENTORY_SLOT_HOVER_GLINT)
        }
    }

    private fun drawCorner(graphics: GuiGraphics, px: Int, py: Int, mirrorX: Boolean, mirrorY: Boolean) {
        val sx = if (mirrorX) -1 else 1
        val sy = if (mirrorY) -1 else 1
        val x = leftPos + px
        val y = topPos + py
        rectRelative(graphics, x, y, x + sx * 6, y + sy, CORNER_GOLD)
        rectRelative(graphics, x, y, x + sx, y + sy * 6, CORNER_GOLD)
        rectRelative(graphics, x + sx * 3, y + sy * 3, x + sx * 6, y + sy * 5, CORNER_AMETHYST)
        pixel(graphics, x + sx * 2, y + sy * 2, CORNER_GLINT)
    }

    private fun drawDiamond(graphics: GuiGraphics, px: Int, py: Int, colour: Int) {
        pixel(graphics, leftPos + px, topPos + py - 2, colour)
        graphics.fill(leftPos + px - 1, topPos + py - 1, leftPos + px + 2, topPos + py + 2, colour)
        pixel(graphics, leftPos + px, topPos + py + 2, colour)
        pixel(graphics, leftPos + px, topPos + py, DIAMOND_CORE)
    }

    private fun line(graphics: GuiGraphics, x1: Int, y1: Int, x2: Int, y2: Int, colour: Int) =
        graphics.fill(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2) + 1, maxOf(y1, y2) + 1, colour)

    private fun rectRelative(graphics: GuiGraphics, x1: Int, y1: Int, x2: Int, y2: Int, colour: Int) =
        graphics.fill(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2) + 1, maxOf(y1, y2) + 1, colour)

    private fun pixel(graphics: GuiGraphics, x: Int, y: Int, colour: Int) =
        graphics.fill(x, y, x + 1, y + 1, colour)

    private fun withAlpha(colour: Int, alpha: Int): Int =
        (colour and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        drawCentred(graphics, title, 88, 9, TITLE)
        drawCentred(
            graphics,
            Component.translatable("screen.hexlove.amethyst_heart.dust_slot"),
            59,
            88,
            LABEL,
        )
        val statusKey = if (menu.isOverflowing) {
            "screen.hexlove.amethyst_heart.overflow"
        } else {
            "screen.hexlove.amethyst_heart.progress"
        }
        val statusColour = if (menu.isOverflowing) OVERFLOW_TEXT else LABEL
        drawCentred(
            graphics,
            Component.translatable(statusKey, (menu.progressPermille + 5) / 10),
            136,
            95,
            statusColour,
        )
        drawCentred(graphics, Component.translatable("container.inventory"), 88, 115, INVENTORY_TEXT)
    }

    private fun drawCentred(graphics: GuiGraphics, text: Component, centerX: Int, y: Int, colour: Int) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, colour, false)
    }

    private fun inside(mouseX: Int, mouseY: Int, x: Int, y: Int, width: Int, height: Int): Boolean =
        mouseX >= leftPos + x && mouseX < leftPos + x + width &&
            mouseY >= topPos + y && mouseY < topPos + y + height

    private companion object {
        const val DUST_SLOT_X = 50
        const val DUST_SLOT_Y = 49
        const val PHIAL_X = 130
        const val PHIAL_Y = 38
        const val PHIAL_WIDTH = 12
        const val PHIAL_HEIGHT = 47
        const val PHIAL_HOVER_X = 120
        const val PHIAL_HOVER_Y = 26
        const val PHIAL_HOVER_WIDTH = 33
        const val PHIAL_HOVER_HEIGHT = 65

        const val OUTER_SHADOW = 0xF207040C.toInt()
        const val FRAME_DARK = 0xFF3A1B42.toInt()
        const val FRAME_GOLD = 0xFFD39A50.toInt()
        const val GOLD_DIM = 0xFF8D6035.toInt()
        const val BACKGROUND_TOP = 0xFA31143F.toInt()
        const val BACKGROUND_BOTTOM = 0xFA11081D.toInt()
        const val AMETHYST_RIM = 0xFF6D447A.toInt()
        const val RITE_PANEL = 0xE91A0A29.toInt()
        const val RITE_BORDER = 0xFFB77A9D.toInt()
        const val PANEL_BOTTOM = 0xD15B3868.toInt()
        const val PANEL_RIGHT = 0xD1694269.toInt()
        const val PANEL_GLINT = 0x38FFF0D6

        const val HEART_SHADOW = 0xDB09040D.toInt()
        const val HEART_GOLD = 0xFFE4AE61.toInt()
        const val HEART_LEFT = 0xFF9D3B7B.toInt()
        const val HEART_RIGHT = 0xFF6E419A.toInt()
        const val HEART_MIDDLE = 0xFF77366F.toInt()
        const val HEART_LOW = 0xFF542752.toInt()
        const val HEART_TIP = 0xFF35172F.toInt()
        const val HEART_GLINT = 0xCFF3A4DE.toInt()
        const val HEART_AURA = 0xFFBD69D5.toInt()
        const val HALO_GOLD = 0xFFEAB96A.toInt()

        const val SLOT_SHADOW = 0xFF08040C.toInt()
        const val SLOT_GOLD = 0xFFD7A25E.toInt()
        const val SLOT_LILAC = 0xFF9B72AA.toInt()
        const val SLOT_INSET = 0xFF321C3A.toInt()
        const val SLOT_WELL = 0xFF100716.toInt()
        const val SLOT_GLINT = 0xBFFFF0C8.toInt()
        const val SLOT_GLINT_DIM = 0x80EAB5D1.toInt()

        const val VINE_GOLD = 0xFFE1B46B.toInt()
        const val VINE_GOLD_DIM = 0xFF9E7143.toInt()
        const val LEAF_ROSE = 0xFFD05A91.toInt()
        const val LEAF_AMETHYST = 0xFF9B69C4.toInt()
        const val LEAF_GLINT = 0xFFF4C1DD.toInt()
        const val FACET_GOLD = 0xFFFFD986.toInt()
        const val FACET_LILAC = 0xFFE7B5FF.toInt()
        const val FACET_WHITE = 0xFFFFF5FF.toInt()

        const val PHIAL_SHADOW = 0xFF07040A.toInt()
        const val GOLD_DARK = 0xFF8B5B2D.toInt()
        const val GOLD_BRIGHT = 0xFFF0C36F.toInt()
        const val GLASS_EDGE = 0xFF765B85.toInt()
        const val GLASS_EMPTY_TOP = 0xDF0D0915.toInt()
        const val GLASS_EMPTY_BOTTOM = 0xEF1C0C2A.toInt()
        const val GLASS_GLINT = 0x459FD1E7
        const val GLASS_GLINT_BRIGHT = 0xA8E9FAFF.toInt()
        const val DUST_LIGHT = 0xFFD7A2FF.toInt()
        const val DUST_MID = 0xFF9E5AD2.toInt()
        const val DUST_DARK = 0xFF5C2E83.toInt()
        const val DUST_RIM = 0xFFF0C7FF.toInt()
        const val DUST_SPARK = 0xFFFFFFFF.toInt()

        const val OVERFLOW_PINK = 0xFFFF61B5.toInt()
        const val OVERFLOW_YELLOW = 0xFFFFDC4A.toInt()
        const val OVERFLOW_WHITE = 0xFFFFFFFF.toInt()
        const val OVERFLOW_RED = 0xFFFF3540.toInt()

        const val DIVIDER_SHADOW = 0xFF0A0510.toInt()
        const val DIVIDER_GOLD = 0xFF9D6B3C.toInt()
        const val DIVIDER_BRIGHT = 0xFFF2C77A.toInt()
        const val DIAMOND_CORE = 0xFFFFF0BE.toInt()
        const val INVENTORY_PANEL = 0xE00E0717.toInt()
        const val INVENTORY_BORDER = 0xFF735077.toInt()
        const val INVENTORY_DRAWER_TOP = 0xFF1B0B28.toInt()
        const val INVENTORY_DRAWER_BOTTOM = 0xFF09040F.toInt()
        const val INVENTORY_RIM = 0xFF624266.toInt()
        const val INVENTORY_RIM_DARK = 0xFF38233D.toInt()
        const val INVENTORY_SLOT_SHADOW = 0xFF09040E.toInt()
        const val INVENTORY_SLOT_WELL = 0xFF16091F.toInt()
        const val INVENTORY_SLOT_HOVER = 0xFF2F153B.toInt()
        const val INVENTORY_SLOT_RIM = 0xFF4F3159.toInt()
        const val INVENTORY_SLOT_GOLD = 0xFF9D7047.toInt()
        const val INVENTORY_SLOT_HOVER_GLINT = 0x90E8B7F4.toInt()
        const val INVENTORY_TAB = 0xFF2B1133.toInt()
        const val INVENTORY_TAB_BORDER = 0xFFAA779E.toInt()
        const val INVENTORY_RULE = 0xFF76506F.toInt()

        const val CORNER_GOLD = 0xFFD7A65E.toInt()
        const val CORNER_AMETHYST = 0xFF754B83.toInt()
        const val CORNER_GLINT = 0xFFFFDEA0.toInt()
        const val TITLE = 0xFFFFE9F7.toInt()
        const val LABEL = 0xFFE8C6E1.toInt()
        const val OVERFLOW_TEXT = 0xFFFF7D73.toInt()
        const val INVENTORY_TEXT = 0xFFE5CFE3.toInt()
        val FACET_POINTS = intArrayOf(
            14, 31, 22, 33, 42, 28, 76, 29, 95, 31, 107, 29,
            17, 47, 102, 43, 113, 50, 17, 59, 100, 57, 109, 65,
            19, 72, 105, 75, 25, 82, 92, 84, 114, 84, 15, 88,
        )
    }
}
