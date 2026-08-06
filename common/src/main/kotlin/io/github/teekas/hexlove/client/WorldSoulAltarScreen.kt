package io.github.teekas.hexlove.client

import io.github.teekas.hexlove.block.WorldSoulMultiblock
import io.github.teekas.hexlove.marriage.RingData
import io.github.teekas.hexlove.menu.WorldSoulAltarMenu
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Heart of the World is presented as a ritual instrument instead of a conventional chest.
 * Its right-hand vessel is a sliver of night sky sealed in gilded amethyst glass; the ring's
 * affection rises through it as a living red nebula. Everything is drawn on the GUI pixel grid,
 * keeping the screen crisp at every Minecraft GUI scale without a large background texture.
 */
class WorldSoulAltarScreen(
    menu: WorldSoulAltarMenu,
    inventory: Inventory,
    title: Component,
) : AbstractContainerScreen<WorldSoulAltarMenu>(menu, inventory, title) {
    private lateinit var attuneButton: CosmicButton
    private lateinit var embodyButton: CosmicButton

    init {
        imageWidth = 232
        imageHeight = 238
    }

    override fun init() {
        super.init()
        attuneButton = ritualButton(
            10,
            118,
            100,
            "screen.hexlove.world_soul_altar.attune",
            WorldSoulAltarMenu.ATTUNE,
            true,
        )
        embodyButton = ritualButton(
            122,
            118,
            100,
            "screen.hexlove.world_soul_altar.embody",
            WorldSoulAltarMenu.EMBODY,
            false,
        )
        addRenderableWidget(attuneButton)
        addRenderableWidget(embodyButton)
    }

    private fun ritualButton(
        x: Int,
        y: Int,
        width: Int,
        key: String,
        action: Int,
        crimson: Boolean,
    ): CosmicButton = CosmicButton(
        leftPos + x,
        topPos + y,
        width,
        20,
        Component.translatable(key),
        {
            minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, action)
        },
        crimson,
    )

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics)
        super.render(graphics, mouseX, mouseY, partialTick)
        renderTooltip(graphics, mouseX, mouseY)

        if (inside(mouseX, mouseY, VESSEL_HOVER_X, VESSEL_HOVER_Y, VESSEL_HOVER_WIDTH, VESSEL_HOVER_HEIGHT)) {
            graphics.renderTooltip(
                font,
                Component.translatable(
                    "screen.hexlove.world_soul_altar.charge_tooltip",
                    String.format(Locale.ROOT, "%.2f", menu.chargeMillis / 1_000.0),
                ),
                mouseX,
                mouseY,
            )
        } else {
            val disabledReason = when {
                attuneButton.isHoveredOrFocused && !attuneButton.active -> attuneDisabledReason()
                embodyButton.isHoveredOrFocused && !embodyButton.active -> embodyDisabledReason()
                else -> null
            }
            if (disabledReason != null) {
                graphics.renderTooltip(font, disabledReason, mouseX, mouseY)
            }
        }
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val time = (minecraft?.level?.gameTime ?: 0L).toFloat() + partialTick
        val awakened = menu.activeStructure
        drawFrame(graphics, time, awakened)
        drawRitualPanel(graphics, time, awakened)
        drawCosmicVessel(graphics, time, awakened)
        drawRitualProgress(graphics, time, awakened)
        drawInventory(graphics)

        val ring = menu.getSlot(1).item
        val playerId = minecraft?.player?.uuid
        val ownsRing = playerId != null && RingData.boundTo(ring) == playerId
        val compatibleLink = RingData.worldSoul(ring) == null || RingData.isWorldLinked(ring, playerId)
        val linked = playerId != null && RingData.isWorldLinked(ring, playerId)
        attuneButton.message = if (linked) DETACH_LABEL else ATTUNE_LABEL
        attuneButton.active = menu.activeStructure && !menu.ritualRunning &&
            !ring.isEmpty && ownsRing && compatibleLink
        embodyButton.active = menu.activeStructure && !menu.ritualRunning &&
            !ring.isEmpty && RingData.isWorldLinked(ring) && menu.chargeMillis >= 1_000
    }

    private fun drawFrame(graphics: GuiGraphics, time: Float, awakened: Boolean) {
        val x = leftPos
        val y = topPos
        val frame = if (awakened) FRAME_AMETHYST else DORMANT_FRAME
        val backgroundTop = if (awakened) BACKGROUND_TOP else DORMANT_BACKGROUND_TOP
        val backgroundBottom = if (awakened) BACKGROUND_BOTTOM else DORMANT_BACKGROUND_BOTTOM
        val divider = if (awakened) GOLD_DIM else DORMANT_DIVIDER
        val rim = if (awakened) AMETHYST_RIM else DORMANT_RIM

        // A stepped, weighty silhouette reads like an enchanted instrument rather than a flat card.
        graphics.fill(x + 4, y + 2, x + imageWidth - 2, y + imageHeight, OUTER_SHADOW)
        graphics.fill(x + 3, y, x + imageWidth - 3, y + imageHeight, FRAME_BLACK)
        graphics.fill(x, y + 3, x + imageWidth, y + imageHeight - 3, FRAME_BLACK)
        graphics.fill(x + 4, y + 1, x + imageWidth - 4, y + imageHeight - 1, frame)
        graphics.fill(x + 2, y + 4, x + imageWidth - 2, y + imageHeight - 4, frame)
        graphics.fillGradient(
            x + 5,
            y + 4,
            x + imageWidth - 5,
            y + imageHeight - 4,
            backgroundTop,
            backgroundBottom,
        )

        graphics.fill(x + 8, y + 20, x + imageWidth - 8, y + 21, divider)
        graphics.fill(x + 20, y + 21, x + imageWidth - 20, y + 22, rim)
        drawHeaderSigil(graphics, 116, 21, awakened)
        drawCorner(graphics, 8, 8, false, false, awakened)
        drawCorner(graphics, imageWidth - 9, 8, true, false, awakened)
        drawCorner(graphics, 8, imageHeight - 9, false, true, awakened)
        drawCorner(graphics, imageWidth - 9, imageHeight - 9, true, true, awakened)
        if (awakened) drawBackgroundStars(graphics, time)
    }

    private fun drawRitualPanel(graphics: GuiGraphics, time: Float, awakened: Boolean) {
        drawSteppedPanel(
            graphics,
            9,
            34,
            158,
            76,
            if (awakened) RITE_TOP else DORMANT_RITE_TOP,
            if (awakened) RITE_BOTTOM else DORMANT_RITE_BOTTOM,
            if (awakened) RITE_BORDER else DORMANT_RITE_BORDER,
        )

        // Four tiny cardinal seals recall the candles that wake the physical multiblock.
        val sealColours = intArrayOf(SEAL_ROSE, SEAL_GOLD, SEAL_PINK, SEAL_VIOLET)
        val sealX = intArrayOf(17, 158, 17, 158)
        val sealY = intArrayOf(41, 41, 101, 101)
        for (index in sealColours.indices) {
            drawDiamond(graphics, sealX[index], sealY[index], 2, if (awakened) sealColours[index] else DORMANT_SEAL)
        }

        drawRitualCircuit(graphics, time, awakened)
        drawSpecialSlot(graphics, AMETHYST_SLOT_X, ALTAR_SLOT_Y, false, time, awakened)
        drawSpecialSlot(graphics, RING_SLOT_X, ALTAR_SLOT_Y, true, time, awakened)

        if (menu.getSlot(0).item.isEmpty) drawShardGhost(graphics, AMETHYST_SLOT_X, ALTAR_SLOT_Y, awakened)
        if (menu.getSlot(1).item.isEmpty) drawRingGhost(graphics, RING_SLOT_X, ALTAR_SLOT_Y, awakened)
    }

    private fun drawRitualCircuit(graphics: GuiGraphics, time: Float, awakened: Boolean) {
        val x = leftPos
        val y = topPos
        val pulse = ((sin(time * 0.16f) + 1f) * 0.5f)
        val dark = if (awakened) THREAD_DARK else DORMANT_THREAD_DARK
        val mid = if (awakened) THREAD_MID else DORMANT_THREAD
        val bright = if (!awakened) DORMANT_THREAD else if (pulse > 0.55f) THREAD_BRIGHT else THREAD_MID

        // The stepped paths deliberately resemble a Hex Casting pattern without copying a glyph.
        graphics.fill(x + 53, y + 64, x + 72, y + 65, dark)
        graphics.fill(x + 69, y + 61, x + 70, y + 65, dark)
        graphics.fill(x + 69, y + 60, x + 83, y + 61, bright)
        graphics.fill(x + 91, y + 60, x + 117, y + 61, dark)
        graphics.fill(x + 94, y + 61, x + 95, y + 66, dark)
        graphics.fill(x + 94, y + 65, x + 117, y + 66, bright)

        drawDiamond(graphics, 87, 63, 6, SIGIL_SHADOW)
        drawDiamond(graphics, 87, 63, 4, if (awakened) SIGIL_AMETHYST else DORMANT_SIGIL)
        drawDiamond(graphics, 87, 63, 2, if (awakened) SIGIL_HEART else DORMANT_SIGIL_CORE)
        pixel(graphics, 87, 63, if (awakened) STAR_WHITE else DORMANT_GLINT)

        // The ring's path continues beyond the panel and disappears into the cosmic vessel.
        graphics.fill(x + 135, y + 64, x + 174, y + 65, dark)
        graphics.fill(x + 139, y + 63, x + 160, y + 64, mid)
        graphics.fill(x + 162, y + 62, x + 180, y + 63, bright)
        if (awakened) {
            for (index in 0..2) {
                val travel = ((time * (0.34f + index * 0.05f) + index * 13f) % 39f).roundToInt()
                pixel(graphics, 139 + travel, 63 - index % 2, if (index == 1) STAR_WHITE else ENERGY_PINK)
            }
        }
    }

    private fun drawSpecialSlot(
        graphics: GuiGraphics,
        slotX: Int,
        slotY: Int,
        ring: Boolean,
        time: Float,
        awakened: Boolean,
    ) {
        val x = leftPos + slotX
        val y = topPos + slotY
        val pulse = sin(time * 0.12f + if (ring) 1.8f else 0f)
        val halo = if (!awakened) DORMANT_SLOT_HALO else if (pulse > 0f) SLOT_HALO_BRIGHT else SLOT_HALO
        val gold = if (awakened) SLOT_GOLD else DORMANT_SLOT_METAL
        val goldDark = if (awakened) SLOT_GOLD_DARK else DORMANT_VESSEL_METAL_DARK
        val rim = if (!awakened) DORMANT_SLOT_RIM else if (ring) SLOT_ROSE else SLOT_AMETHYST

        drawDiamond(graphics, slotX + 8, slotY + 8, 17, if (awakened) SLOT_AURA else DORMANT_SLOT_AURA)
        drawDiamond(graphics, slotX + 8, slotY + 8, 14, halo)
        graphics.fill(x - 3, y - 3, x + 19, y + 19, SLOT_SHADOW)
        graphics.fill(x - 2, y - 2, x + 18, y + 18, gold)
        graphics.fill(x - 1, y - 1, x + 17, y + 17, rim)
        graphics.fillGradient(
            x,
            y,
            x + 16,
            y + 16,
            if (awakened) SLOT_WELL_TOP else DORMANT_SLOT_WELL_TOP,
            if (awakened) SLOT_WELL_BOTTOM else DORMANT_SLOT_WELL_BOTTOM,
        )
        graphics.fill(x, y, x + 16, y + 1, if (awakened) SLOT_GLINT else DORMANT_SLOT_GLINT)
        graphics.fill(x, y, x + 1, y + 16, if (awakened) SLOT_GLINT_DIM else DORMANT_SLOT_GLINT)

        // Four prongs visibly hold the item above the dark well.
        graphics.fill(x - 4, y - 2, x, y, if (awakened) SLOT_GOLD_BRIGHT else DORMANT_SLOT_METAL_LIGHT)
        graphics.fill(x + 16, y - 2, x + 20, y, if (awakened) SLOT_GOLD_BRIGHT else DORMANT_SLOT_METAL_LIGHT)
        graphics.fill(x - 4, y + 16, x, y + 18, goldDark)
        graphics.fill(x + 16, y + 16, x + 20, y + 18, goldDark)
    }

    private fun drawShardGhost(graphics: GuiGraphics, slotX: Int, slotY: Int, awakened: Boolean) {
        val cx = slotX + 8
        val cy = slotY + 8
        drawDiamond(graphics, cx, cy, 5, if (awakened) GHOST_AMETHYST else DORMANT_GHOST)
        drawDiamond(graphics, cx, cy, 2, if (awakened) GHOST_GLINT else DORMANT_GHOST_GLINT)
        pixel(graphics, cx - 1, cy - 3, if (awakened) GHOST_WHITE else DORMANT_GHOST_GLINT)
    }

    private fun drawRingGhost(graphics: GuiGraphics, slotX: Int, slotY: Int, awakened: Boolean) {
        val x = leftPos + slotX
        val y = topPos + slotY
        val ring = if (awakened) GHOST_ROSE else DORMANT_GHOST
        graphics.fill(x + 4, y + 3, x + 12, y + 4, ring)
        graphics.fill(x + 2, y + 5, x + 4, y + 11, ring)
        graphics.fill(x + 12, y + 5, x + 14, y + 11, ring)
        graphics.fill(x + 4, y + 12, x + 12, y + 13, ring)
        drawDiamond(graphics, slotX + 8, slotY + 3, 2, if (awakened) GHOST_GOLD else DORMANT_GHOST_GLINT)
    }

    /** Draws the signature object: a star-filled reliquary whose nebula is the real ring charge. */
    private fun drawCosmicVessel(graphics: GuiGraphics, time: Float, awakened: Boolean) {
        val cx = leftPos + VESSEL_CENTER_X
        val chamberTop = topPos + VESSEL_CHAMBER_TOP
        val goldDark = if (awakened) VESSEL_GOLD_DARK else DORMANT_VESSEL_METAL_DARK
        val gold = if (awakened) VESSEL_GOLD else DORMANT_VESSEL_METAL
        val goldBright = if (awakened) VESSEL_GOLD_BRIGHT else DORMANT_VESSEL_METAL_LIGHT
        val glassBorder = if (awakened) GLASS_BORDER else DORMANT_GLASS_BORDER
        val voidTop = if (awakened) VOID_TOP else DORMANT_VOID_TOP
        val voidBottom = if (awakened) VOID_BOTTOM else DORMANT_VOID_BOTTOM

        if (awakened) drawVesselHalo(graphics, cx, chamberTop, time)

        // Crown, stopper, and narrow neck.
        graphics.fill(cx - 7, topPos + 32, cx + 8, topPos + 34, goldDark)
        graphics.fill(cx - 9, topPos + 34, cx + 10, topPos + 36, gold)
        graphics.fill(cx - 6, topPos + 36, cx + 7, topPos + 38, goldBright)
        graphics.fill(cx - 5, topPos + 38, cx + 6, topPos + 46, glassBorder)
        graphics.fillGradient(cx - 3, topPos + 39, cx + 4, topPos + 46, voidTop, voidBottom)
        graphics.fill(cx - 3, topPos + 39, cx - 2, topPos + 45, if (awakened) GLASS_GLINT else DORMANT_GLASS_GLINT)

        // Each scanline follows a faceted bottle silhouette; no rectangular progress bar remains.
        for (localY in 0..VESSEL_CHAMBER_LAST_ROW) {
            val half = vesselOuterHalfWidth(localY)
            val py = chamberTop + localY
            val border = when {
                !awakened -> glassBorder
                localY <= 4 -> GLASS_RIM_BRIGHT
                localY >= VESSEL_CHAMBER_LAST_ROW - 4 -> goldDark
                localY % 7 == 0 -> GLASS_BORDER_LIGHT
                else -> glassBorder
            }
            graphics.fill(cx - half, py, cx + half + 1, py + 1, border)
            val inner = (half - 2).coerceAtLeast(1)
            graphics.fill(cx - inner, py, cx + inner + 1, py + 1, if (localY < 18) voidTop else voidBottom)
        }

        if (awakened) {
            drawVesselStars(graphics, cx, chamberTop, time)
            drawVesselEnergy(graphics, cx, chamberTop, time)
        }

        // Glass glare and gold foot are drawn last so the energy appears sealed behind them.
        for (localY in 7..42) {
            val half = vesselOuterHalfWidth(localY)
            val py = chamberTop + localY
            val glint = if (!awakened) {
                DORMANT_GLASS_GLINT
            } else if (localY % 5 == 0) {
                GLASS_GLINT_BRIGHT
            } else {
                GLASS_GLINT
            }
            pixelAbsolute(graphics, cx - half + 2, py, glint)
        }
        graphics.fill(cx - 10, chamberTop + 53, cx + 11, chamberTop + 55, goldDark)
        graphics.fill(cx - 13, chamberTop + 55, cx + 14, chamberTop + 57, gold)
        graphics.fill(cx - 9, chamberTop + 57, cx + 10, chamberTop + 59, goldBright)
        if (awakened) {
            pixelAbsolute(graphics, cx - 5, chamberTop + 57, STAR_WHITE)
            pixelAbsolute(graphics, cx + 6, chamberTop + 56, ENERGY_PINK)
        }
    }

    private fun drawVesselHalo(graphics: GuiGraphics, cx: Int, chamberTop: Int, time: Float) {
        val awakened = menu.chargeMillis > 0 || menu.ritualRunning
        val pulse = sin(time * if (menu.ritualRunning) 0.35f else 0.12f)
        val outer = if (awakened && pulse > 0f) VESSEL_HALO_BRIGHT else VESSEL_HALO
        graphics.fill(cx - 22, chamberTop + 8, cx + 23, chamberTop + 46, VESSEL_HALO_FAINT)
        graphics.fill(cx - 19, chamberTop + 4, cx + 20, chamberTop + 50, outer)
        graphics.fill(cx - 16, chamberTop + 1, cx + 17, chamberTop + 53, VESSEL_HALO_INNER)
    }

    private fun drawVesselStars(graphics: GuiGraphics, cx: Int, chamberTop: Int, time: Float) {
        val stars = intArrayOf(
            -7, 8,
            8, 12,
            -11, 19,
            3, 23,
            11, 31,
            -5, 36,
            6, 43,
            -9, 45,
        )
        for (index in stars.indices step 2) {
            val starIndex = index / 2
            val sx = cx + stars[index]
            val sy = chamberTop + stars[index + 1]
            val twinkle = sin(time * (0.11f + starIndex * 0.013f) + starIndex * 2.1f)
            val colour = when {
                twinkle > 0.55f -> STAR_WHITE
                starIndex % 3 == 0 -> STAR_ROSE
                else -> STAR_VIOLET
            }
            pixelAbsolute(graphics, sx, sy, colour)
            if (twinkle > 0.82f) {
                pixelAbsolute(graphics, sx - 1, sy, STAR_FAINT)
                pixelAbsolute(graphics, sx + 1, sy, STAR_FAINT)
                pixelAbsolute(graphics, sx, sy - 1, STAR_FAINT)
                pixelAbsolute(graphics, sx, sy + 1, STAR_FAINT)
            }
        }
    }

    private fun drawVesselEnergy(graphics: GuiGraphics, cx: Int, chamberTop: Int, time: Float) {
        if (menu.chargeMillis <= 0) return
        val fraction = menu.chargeMillis / WorldSoulAltarMenu.MAX_CHARGE_MILLIS.toFloat()
        val fillRows = ceil(VESSEL_ENERGY_ROWS * fraction).toInt().coerceIn(1, VESSEL_ENERGY_ROWS)
        val bottom = VESSEL_ENERGY_BOTTOM
        val top = bottom - fillRows + 1

        for (localY in top..bottom) {
            val half = (vesselOuterHalfWidth(localY) - 3).coerceAtLeast(1)
            val py = chamberTop + localY
            val depth = (localY - top).toFloat() / fillRows.coerceAtLeast(1)
            val outerColour = if ((localY + time.toInt()) % 6 == 0) ENERGY_RED_BRIGHT else ENERGY_RED
            graphics.fill(cx - half, py, cx + half + 1, py + 1, outerColour)
            val coreHalf = (half * (0.40f + depth * 0.18f)).roundToInt().coerceAtLeast(1)
            graphics.fill(cx - coreHalf, py, cx + coreHalf + 1, py + 1, ENERGY_CORE)
            if ((localY + time.toInt()) % 9 == 0) {
                pixelAbsolute(graphics, cx, py, ENERGY_WHITE)
            }
        }

        // A restless, asymmetrical meniscus keeps the charge feeling alive rather than measured.
        val surfaceHalf = (vesselOuterHalfWidth(top) - 3).coerceAtLeast(1)
        val surfaceY = chamberTop + top
        graphics.fill(cx - surfaceHalf, surfaceY, cx + surfaceHalf + 1, surfaceY + 1, ENERGY_SURFACE)
        val crest = sin(time * 0.23f).roundToInt()
        graphics.fill(cx - 4 + crest, surfaceY - 1, cx + 5 + crest, surfaceY, ENERGY_PINK)
        pixelAbsolute(graphics, cx + 7 - crest, surfaceY - 1, ENERGY_WHITE)

        val moteCount = ceil(fraction * 9f).toInt().coerceAtLeast(1)
        for (index in 0 until moteCount) {
            val travel = ((time * (0.23f + index * 0.017f) + index * 7.3f) % fillRows).roundToInt()
            val localY = (bottom - travel).coerceIn(top, bottom)
            val half = (vesselOuterHalfWidth(localY) - 5).coerceAtLeast(1)
            val drift = (sin(time * 0.16f + index * 2.4f) * half).roundToInt()
            val colour = if (index % 4 == 0) ENERGY_WHITE else if (index % 2 == 0) ENERGY_PINK else ENERGY_ORB
            pixelAbsolute(graphics, cx + drift, chamberTop + localY, colour)
        }
    }

    private fun vesselOuterHalfWidth(localY: Int): Int = when (localY) {
        0 -> 8
        1 -> 10
        2 -> 12
        3 -> 14
        4 -> 16
        in 5..42 -> 18
        43 -> 17
        44 -> 16
        45 -> 15
        46 -> 14
        47 -> 13
        48 -> 12
        49 -> 11
        50 -> 10
        51 -> 9
        else -> 8
    }

    private fun drawRitualProgress(graphics: GuiGraphics, time: Float, awakened: Boolean) {
        val x = leftPos
        val y = topPos
        graphics.fill(x + 12, y + 111, x + 164, y + 113, PROGRESS_SHADOW)
        graphics.fill(x + 15, y + 111, x + 161, y + 112, if (awakened) PROGRESS_TRACK else DORMANT_PROGRESS_TRACK)
        drawDiamond(graphics, 88, 112, 2, if (awakened) PROGRESS_KNOT else DORMANT_PROGRESS_KNOT)
        if (!awakened || !menu.ritualRunning) return

        val width = 146 * menu.ritualProgress / 1_000
        graphics.fillGradient(x + 15, y + 111, x + 15 + width, y + 113, ENERGY_PINK, SIGIL_AMETHYST)
        val sparkX = (15 + width).coerceAtMost(160)
        pixel(graphics, sparkX, 110, if (sin(time * 0.6f) > 0f) STAR_WHITE else ENERGY_PINK)
    }

    private fun drawInventory(graphics: GuiGraphics) {
        drawSteppedPanel(graphics, 24, 140, 184, 92, INVENTORY_TOP, INVENTORY_BOTTOM, INVENTORY_BORDER)
        graphics.fill(leftPos + 33, topPos + 154, leftPos + 199, topPos + 155, INVENTORY_RIM)
        graphics.fill(leftPos + 33, topPos + 231, leftPos + 199, topPos + 232, INVENTORY_RIM_DARK)

        for (row in 0 until 3) {
            for (column in 0 until 9) {
                drawInventorySlot(graphics, INVENTORY_X + column * 18, INVENTORY_Y + row * 18, false)
            }
        }
        for (column in 0 until 9) {
            drawInventorySlot(graphics, INVENTORY_X + column * 18, HOTBAR_Y, true)
        }
    }

    private fun drawInventorySlot(graphics: GuiGraphics, slotX: Int, slotY: Int, hotbar: Boolean) {
        val x = leftPos + slotX
        val y = topPos + slotY
        graphics.fill(x - 1, y - 1, x + 17, y + 17, if (hotbar) HOTBAR_GOLD else INVENTORY_SLOT_EDGE)
        graphics.fill(x, y, x + 16, y + 16, INVENTORY_SLOT_RIM)
        graphics.fillGradient(x + 1, y + 1, x + 15, y + 15, INVENTORY_SLOT_TOP, INVENTORY_SLOT_BOTTOM)
        graphics.fill(x + 1, y + 1, x + 15, y + 2, INVENTORY_SLOT_GLINT)
        if (hotbar) {
            graphics.fill(x + 3, y + 15, x + 13, y + 16, HOTBAR_GOLD_DARK)
        }
    }

    private fun drawSteppedPanel(
        graphics: GuiGraphics,
        px: Int,
        py: Int,
        width: Int,
        height: Int,
        top: Int,
        bottom: Int,
        border: Int,
    ) {
        val x = leftPos + px
        val y = topPos + py
        graphics.fill(x + 2, y, x + width - 2, y + height, border)
        graphics.fill(x, y + 2, x + width, y + height - 2, border)
        graphics.fillGradient(x + 2, y + 2, x + width - 2, y + height - 2, top, bottom)
        graphics.fill(x + 4, y + 2, x + width - 4, y + 3, PANEL_GLINT)
        graphics.fill(x + 3, y + height - 3, x + width - 3, y + height - 2, PANEL_SHADOW)
    }

    private fun drawBackgroundStars(graphics: GuiGraphics, time: Float) {
        val stars = intArrayOf(
            12, 27,
            222, 27,
            5, 73,
            226, 89,
            15, 129,
            216, 133,
            15, 178,
            217, 198,
            22, 225,
            210, 224,
        )
        for (index in stars.indices step 2) {
            val twinkle = sin(time * 0.08f + index * 1.7f)
            pixel(graphics, stars[index], stars[index + 1], if (twinkle > 0.35f) STAR_ROSE else STAR_DIM)
        }
    }

    private fun drawHeaderSigil(graphics: GuiGraphics, cx: Int, cy: Int, awakened: Boolean) {
        drawDiamond(graphics, cx, cy, 4, HEADER_SHADOW)
        drawDiamond(graphics, cx, cy, 2, if (awakened) HEADER_GOLD else DORMANT_HEADER)
        pixel(graphics, cx, cy, if (awakened) STAR_WHITE else DORMANT_GLINT)
    }

    private fun drawCorner(
        graphics: GuiGraphics,
        px: Int,
        py: Int,
        mirrorX: Boolean,
        mirrorY: Boolean,
        awakened: Boolean,
    ) {
        val sx = if (mirrorX) -1 else 1
        val sy = if (mirrorY) -1 else 1
        val ox = leftPos + px
        val oy = topPos + py
        val metal = if (awakened) CORNER_GOLD else DORMANT_CORNER_METAL
        val gem = if (awakened) CORNER_AMETHYST else DORMANT_CORNER_GEM
        fillRelative(graphics, ox, oy, ox + sx * 7, oy + sy, metal)
        fillRelative(graphics, ox, oy, ox + sx, oy + sy * 7, metal)
        fillRelative(graphics, ox + sx * 3, oy + sy * 3, ox + sx * 5, oy + sy * 5, gem)
        pixelAbsolute(graphics, ox + sx * 3, oy + sy * 3, if (awakened) CORNER_GLINT else DORMANT_GLINT)
    }

    private fun drawDiamond(graphics: GuiGraphics, centerX: Int, centerY: Int, radius: Int, colour: Int) {
        val cx = leftPos + centerX
        val cy = topPos + centerY
        for (dy in -radius..radius) {
            val half = radius - abs(dy)
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour)
        }
    }

    private fun pixel(graphics: GuiGraphics, px: Int, py: Int, colour: Int) {
        pixelAbsolute(graphics, leftPos + px, topPos + py, colour)
    }

    private fun pixelAbsolute(graphics: GuiGraphics, px: Int, py: Int, colour: Int) {
        graphics.fill(px, py, px + 1, py + 1, colour)
    }

    private fun fillRelative(graphics: GuiGraphics, x1: Int, y1: Int, x2: Int, y2: Int, colour: Int) {
        graphics.fill(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2) + 1, maxOf(y1, y2) + 1, colour)
    }

    private fun centered(graphics: GuiGraphics, text: Component, centerX: Int, y: Int, colour: Int) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, colour, false)
    }

    override fun renderLabels(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        centered(graphics, title, 116, 8, if (menu.activeStructure) TITLE else DORMANT_TITLE)
        centered(
            graphics,
            structureStatus(),
            116,
            24,
            if (menu.activeStructure) ACTIVE_TEXT else INACTIVE_TEXT,
        )
        val ritualLabel = if (menu.activeStructure) LABEL else DORMANT_LABEL
        centered(graphics, AMETHYST_LABEL, 42, 84, ritualLabel)
        centered(graphics, RING_LABEL, 124, 84, ritualLabel)
        centered(
            graphics,
            chargeLabel(),
            VESSEL_CENTER_X,
            108,
            if (menu.activeStructure) CHARGE_TEXT else DORMANT_CHARGE_TEXT,
        )
        centered(graphics, INVENTORY_LABEL, 116, 144, INVENTORY_TEXT)
    }

    /** The charge only ever moves in whole millis, so the string is re-cut when it actually changes. */
    private var chargeLabelFor = Int.MIN_VALUE
    private var chargeLabelText: Component = Component.empty()

    private fun chargeLabel(): Component {
        if (menu.chargeMillis != chargeLabelFor) {
            chargeLabelFor = menu.chargeMillis
            chargeLabelText = Component.literal(String.format(Locale.ROOT, "%.2f", menu.chargeMillis / 1_000.0))
        }
        return chargeLabelText
    }

    private fun structureStatus(): Component {
        if (menu.activeStructure) return Component.translatable("screen.hexlove.world_soul_altar.active")
        val key = when (menu.structureProblem) {
            WorldSoulMultiblock.Problem.DAIS.code -> "screen.hexlove.world_soul_altar.incomplete_dais"
            WorldSoulMultiblock.Problem.PILLAR_FOUNDATIONS.code ->
                "screen.hexlove.world_soul_altar.incomplete_foundations"
            WorldSoulMultiblock.Problem.FLOWERS.code -> "screen.hexlove.world_soul_altar.incomplete_flowers"
            WorldSoulMultiblock.Problem.PILLARS_OR_CANDLES.code ->
                "screen.hexlove.world_soul_altar.incomplete_pillars"
            else -> "screen.hexlove.world_soul_altar.inactive"
        }
        return Component.translatable(key)
    }

    private fun attuneDisabledReason(): Component? = when {
        !menu.activeStructure -> Component.translatable("screen.hexlove.world_soul_altar.structure_incomplete")
        menu.ritualRunning -> null
        menu.getSlot(1).item.isEmpty ||
            RingData.boundTo(menu.getSlot(1).item) != minecraft?.player?.uuid ->
            Component.translatable("screen.hexlove.world_soul_altar.needs_bound_ring")
        else -> null
    }

    private fun embodyDisabledReason(): Component? = when {
        !menu.activeStructure -> Component.translatable("screen.hexlove.world_soul_altar.structure_incomplete")
        menu.ritualRunning -> null
        menu.getSlot(1).item.isEmpty || !RingData.isWorldLinked(menu.getSlot(1).item) ->
            Component.translatable("screen.hexlove.world_soul_altar.needs_linked_ring")
        menu.chargeMillis < 1_000 || menu.getSlot(0).item.isEmpty ->
            Component.translatable("screen.hexlove.world_soul_altar.not_enough_affection")
        else -> null
    }

    private fun inside(mouseX: Int, mouseY: Int, x: Int, y: Int, width: Int, height: Int): Boolean =
        mouseX >= leftPos + x && mouseX < leftPos + x + width &&
            mouseY >= topPos + y && mouseY < topPos + y + height

    private class CosmicButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        message: Component,
        onPress: OnPress,
        private val crimson: Boolean,
    ) : Button(x, y, width, height, message, onPress, DEFAULT_NARRATION) {
        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            val hovered = isHoveredOrFocused && active
            val border = when {
                !active -> BUTTON_DISABLED_BORDER
                hovered -> BUTTON_HOVER_BORDER
                crimson -> BUTTON_CRIMSON_BORDER
                else -> BUTTON_VIOLET_BORDER
            }
            val top = when {
                !active -> BUTTON_DISABLED_TOP
                hovered && crimson -> BUTTON_CRIMSON_HOVER
                hovered -> BUTTON_VIOLET_HOVER
                crimson -> BUTTON_CRIMSON_TOP
                else -> BUTTON_VIOLET_TOP
            }
            val bottom = if (active) BUTTON_BOTTOM else BUTTON_DISABLED_BOTTOM

            graphics.fill(x + 2, y + 1, x + width - 1, y + height, BUTTON_SHADOW)
            graphics.fill(x + 2, y, x + width - 2, y + height - 1, border)
            graphics.fill(x, y + 2, x + width, y + height - 3, border)
            graphics.fillGradient(x + 2, y + 2, x + width - 2, y + height - 3, top, bottom)
            graphics.fill(x + 5, y + 3, x + width - 5, y + 4, if (active) BUTTON_GLINT else BUTTON_DISABLED_GLINT)
            graphics.fill(x + 7, y + height - 4, x + width - 7, y + height - 3, BUTTON_INSET)

            // The two end gems are small, functional focus markers rather than generic rounded corners.
            graphics.fill(x + 2, y + height / 2 - 1, x + 4, y + height / 2 + 1, border)
            graphics.fill(x + width - 4, y + height / 2 - 1, x + width - 2, y + height / 2 + 1, border)
            graphics.drawCenteredString(
                Minecraft.getInstance().font,
                message,
                x + width / 2,
                y + (height - 8) / 2,
                when {
                    !active -> BUTTON_DISABLED_TEXT
                    hovered -> BUTTON_TEXT_HOVER
                    else -> BUTTON_TEXT
                },
            )
        }
    }

    private companion object {
        // Fixed labels: renderLabels/renderBg run every frame, and a new Component for each of
        // them was pure garbage. Nothing here depends on state, so one instance lives forever.
        val AMETHYST_LABEL: Component = Component.translatable("screen.hexlove.world_soul_altar.amethyst")
        val RING_LABEL: Component = Component.translatable("screen.hexlove.world_soul_altar.ring")
        val INVENTORY_LABEL: Component = Component.translatable("container.inventory")
        val ATTUNE_LABEL: Component = Component.translatable("screen.hexlove.world_soul_altar.attune")
        val DETACH_LABEL: Component = Component.translatable("screen.hexlove.world_soul_altar.detach")

        const val AMETHYST_SLOT_X = 34
        const val RING_SLOT_X = 116
        const val ALTAR_SLOT_Y = 56
        const val INVENTORY_X = 35
        const val INVENTORY_Y = 157
        const val HOTBAR_Y = 213
        const val VESSEL_CENTER_X = 199
        const val VESSEL_CHAMBER_TOP = 47
        const val VESSEL_CHAMBER_LAST_ROW = 52
        const val VESSEL_ENERGY_ROWS = 49
        const val VESSEL_ENERGY_BOTTOM = 50
        const val VESSEL_HOVER_X = 176
        const val VESSEL_HOVER_Y = 31
        const val VESSEL_HOVER_WIDTH = 46
        const val VESSEL_HOVER_HEIGHT = 84

        const val OUTER_SHADOW = 0xE705020A.toInt()
        const val FRAME_BLACK = 0xFF08040E.toInt()
        const val FRAME_AMETHYST = 0xFF593062.toInt()
        const val BACKGROUND_TOP = 0xFA24102F.toInt()
        const val BACKGROUND_BOTTOM = 0xFA0A0612.toInt()
        const val GOLD_DIM = 0xFF6B4B43.toInt()
        const val AMETHYST_RIM = 0xFF81528D.toInt()
        const val CORNER_GOLD = 0xFFC18B61.toInt()
        const val CORNER_AMETHYST = 0xFF7A4D83.toInt()
        const val CORNER_GLINT = 0xFFFFD8B1.toInt()
        const val HEADER_SHADOW = 0xFF24102F.toInt()
        const val HEADER_GOLD = 0xFFE3B46F.toInt()
        const val DORMANT_FRAME = 0xFF302A33.toInt()
        const val DORMANT_BACKGROUND_TOP = 0xFA151218.toInt()
        const val DORMANT_BACKGROUND_BOTTOM = 0xFA08070A.toInt()
        const val DORMANT_DIVIDER = 0xFF40383E.toInt()
        const val DORMANT_RIM = 0xFF382F3B.toInt()
        const val DORMANT_HEADER = 0xFF62575D.toInt()
        const val DORMANT_CORNER_METAL = 0xFF554A46.toInt()
        const val DORMANT_CORNER_GEM = 0xFF433944.toInt()
        const val DORMANT_GLINT = 0xFF6B626C.toInt()

        const val RITE_TOP = 0xF31C0A2A.toInt()
        const val RITE_BOTTOM = 0xF30B0614.toInt()
        const val RITE_BORDER = 0xFF74447B.toInt()
        const val PANEL_GLINT = 0x787E537F
        const val PANEL_SHADOW = 0xC607040B.toInt()
        const val SEAL_ROSE = 0xFFBF4669.toInt()
        const val SEAL_GOLD = 0xFFD3A65E.toInt()
        const val SEAL_PINK = 0xFFE777B5.toInt()
        const val SEAL_VIOLET = 0xFF865BCE.toInt()
        const val DORMANT_RITE_TOP = 0xF3121015.toInt()
        const val DORMANT_RITE_BOTTOM = 0xF308070A.toInt()
        const val DORMANT_RITE_BORDER = 0xFF39323D.toInt()
        const val DORMANT_SEAL = 0xFF4A404A.toInt()

        const val SLOT_AURA = 0x302B0D31
        const val SLOT_HALO = 0x554C2855
        const val SLOT_HALO_BRIGHT = 0x72743E75
        const val SLOT_SHADOW = 0xFF07030B.toInt()
        const val SLOT_GOLD = 0xFFCF9A5C.toInt()
        const val SLOT_GOLD_BRIGHT = 0xFFF1C783.toInt()
        const val SLOT_GOLD_DARK = 0xFF67422F.toInt()
        const val SLOT_AMETHYST = 0xFF9865A9.toInt()
        const val SLOT_ROSE = 0xFFB34D76.toInt()
        const val SLOT_WELL_TOP = 0xFF210B29.toInt()
        const val SLOT_WELL_BOTTOM = 0xFF09040E.toInt()
        const val SLOT_GLINT = 0x99FFE0F4.toInt()
        const val SLOT_GLINT_DIM = 0x668C638D
        const val GHOST_AMETHYST = 0x665F3B77
        const val GHOST_GLINT = 0x888F62A8.toInt()
        const val GHOST_WHITE = 0x99F1D8FF.toInt()
        const val GHOST_ROSE = 0x776B304D
        const val GHOST_GOLD = 0x99D5A15C.toInt()
        const val DORMANT_SLOT_AURA = 0x18100E12
        const val DORMANT_SLOT_HALO = 0x332B2630
        const val DORMANT_SLOT_METAL = 0xFF594E49.toInt()
        const val DORMANT_SLOT_METAL_LIGHT = 0xFF786B62.toInt()
        const val DORMANT_SLOT_RIM = 0xFF493E4C.toInt()
        const val DORMANT_SLOT_WELL_TOP = 0xFF151217.toInt()
        const val DORMANT_SLOT_WELL_BOTTOM = 0xFF08070A.toInt()
        const val DORMANT_SLOT_GLINT = 0x444C454F
        const val DORMANT_GHOST = 0x55433C47
        const val DORMANT_GHOST_GLINT = 0x665E5662

        const val THREAD_DARK = 0xAA4C244A.toInt()
        const val THREAD_MID = 0xD9904D78.toInt()
        const val THREAD_BRIGHT = 0xFFFF8FBD.toInt()
        const val SIGIL_SHADOW = 0xFF17071F.toInt()
        const val SIGIL_AMETHYST = 0xFF7849AC.toInt()
        const val SIGIL_HEART = 0xFFFF3F76.toInt()
        const val DORMANT_THREAD_DARK = 0xFF241E26.toInt()
        const val DORMANT_THREAD = 0xFF332A34.toInt()
        const val DORMANT_SIGIL = 0xFF3D3441.toInt()
        const val DORMANT_SIGIL_CORE = 0xFF493A41.toInt()

        const val VESSEL_HALO_FAINT = 0x0FFF255D
        const val VESSEL_HALO = 0x16B42D63
        const val VESSEL_HALO_BRIGHT = 0x28FF3A74
        const val VESSEL_HALO_INNER = 0x244E2B73
        const val VESSEL_GOLD_DARK = 0xFF6D432E.toInt()
        const val VESSEL_GOLD = 0xFFD09B58.toInt()
        const val VESSEL_GOLD_BRIGHT = 0xFFFFD58A.toInt()
        const val GLASS_BORDER = 0xFF765184.toInt()
        const val GLASS_BORDER_LIGHT = 0xFFB07BAD.toInt()
        const val GLASS_RIM_BRIGHT = 0xFFD5A6D1.toInt()
        const val GLASS_GLINT = 0x72F8D8FF
        const val GLASS_GLINT_BRIGHT = 0xB8FFF1FF.toInt()
        const val VOID_TOP = 0xFF100B20.toInt()
        const val VOID_BOTTOM = 0xFF05030B.toInt()
        const val DORMANT_VESSEL_METAL_DARK = 0xFF3A3230.toInt()
        const val DORMANT_VESSEL_METAL = 0xFF5A504B.toInt()
        const val DORMANT_VESSEL_METAL_LIGHT = 0xFF756A63.toInt()
        const val DORMANT_GLASS_BORDER = 0xFF4C4550.toInt()
        const val DORMANT_GLASS_GLINT = 0x444D4852
        const val DORMANT_VOID_TOP = 0xFF0C0B0E.toInt()
        const val DORMANT_VOID_BOTTOM = 0xFF050507.toInt()
        const val ENERGY_RED = 0xFFE11242.toInt()
        const val ENERGY_RED_BRIGHT = 0xFFF52555.toInt()
        const val ENERGY_CORE = 0xFFFF3B69.toInt()
        const val ENERGY_SURFACE = 0xFFFF7AA0.toInt()
        const val ENERGY_PINK = 0xFFFF5F94.toInt()
        const val ENERGY_ORB = 0xFFFF1C4D.toInt()
        const val ENERGY_WHITE = 0xFFFFE4ED.toInt()
        const val STAR_WHITE = 0xFFFFF1FA.toInt()
        const val STAR_ROSE = 0xFFFF86B8.toInt()
        const val STAR_VIOLET = 0xFFC89AFF.toInt()
        const val STAR_FAINT = 0x66E9B8FF
        const val STAR_DIM = 0x665A326B

        const val PROGRESS_SHADOW = 0xFF08030C.toInt()
        const val PROGRESS_TRACK = 0xFF492143.toInt()
        const val PROGRESS_KNOT = 0xFFD5A45F.toInt()
        const val DORMANT_PROGRESS_TRACK = 0xFF29242B.toInt()
        const val DORMANT_PROGRESS_KNOT = 0xFF5C5250.toInt()

        const val INVENTORY_TOP = 0xEF180923.toInt()
        const val INVENTORY_BOTTOM = 0xF008050E.toInt()
        const val INVENTORY_BORDER = 0xFF644069.toInt()
        const val INVENTORY_RIM = 0xFF7E527C.toInt()
        const val INVENTORY_RIM_DARK = 0xFF342039.toInt()
        const val INVENTORY_SLOT_EDGE = 0xFF65416B.toInt()
        const val INVENTORY_SLOT_RIM = 0xFF2F1937.toInt()
        const val INVENTORY_SLOT_TOP = 0xFF180A20.toInt()
        const val INVENTORY_SLOT_BOTTOM = 0xFF08040D.toInt()
        const val INVENTORY_SLOT_GLINT = 0x556F4D76
        const val HOTBAR_GOLD = 0xFF9B6D51.toInt()
        const val HOTBAR_GOLD_DARK = 0xFF52372F.toInt()

        const val TITLE = 0xFFFFEAF7.toInt()
        const val LABEL = 0xFFDABBD6.toInt()
        const val ACTIVE_TEXT = 0xFFFF83B1.toInt()
        const val INACTIVE_TEXT = 0xFF9F8B9F.toInt()
        const val CHARGE_TEXT = 0xFFFFCADC.toInt()
        const val INVENTORY_TEXT = 0xFFD9C1D8.toInt()
        const val DORMANT_TITLE = 0xFFA39AA4.toInt()
        const val DORMANT_LABEL = 0xFF746C76.toInt()
        const val DORMANT_CHARGE_TEXT = 0xFF625B64.toInt()

        const val BUTTON_SHADOW = 0xD5050208.toInt()
        const val BUTTON_DISABLED_BORDER = 0xFF4A394B.toInt()
        const val BUTTON_CRIMSON_BORDER = 0xFFC45370.toInt()
        const val BUTTON_VIOLET_BORDER = 0xFF8762B2.toInt()
        const val BUTTON_HOVER_BORDER = 0xFFFFD38B.toInt()
        const val BUTTON_DISABLED_TOP = 0xFF2B222E.toInt()
        const val BUTTON_DISABLED_BOTTOM = 0xFF151017.toInt()
        const val BUTTON_CRIMSON_TOP = 0xFF72223E.toInt()
        const val BUTTON_CRIMSON_HOVER = 0xFF9C2E4D.toInt()
        const val BUTTON_VIOLET_TOP = 0xFF4D2C70.toInt()
        const val BUTTON_VIOLET_HOVER = 0xFF68408B.toInt()
        const val BUTTON_BOTTOM = 0xFF240D22.toInt()
        const val BUTTON_GLINT = 0x55FFD9E5
        const val BUTTON_DISABLED_GLINT = 0x334D3E50
        const val BUTTON_INSET = 0xAA160817.toInt()
        const val BUTTON_TEXT = 0xFFFFE5F2.toInt()
        const val BUTTON_TEXT_HOVER = 0xFFFFF2CE.toInt()
        const val BUTTON_DISABLED_TEXT = 0xFF786B79.toInt()
    }
}
