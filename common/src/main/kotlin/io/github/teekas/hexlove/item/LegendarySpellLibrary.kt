package io.github.teekas.hexlove.item

import at.petrak.hexcasting.api.casting.iota.Iota
import at.petrak.hexcasting.api.casting.iota.ListIota
import at.petrak.hexcasting.api.casting.iota.PatternIota
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.api.item.IotaHolderItem
import at.petrak.hexcasting.common.items.storage.ItemFocus
import at.petrak.hexcasting.common.lib.HexItems
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.nio.charset.StandardCharsets

/** Loads the author's exact angle-signature programs and turns them into sealed Hex Casting foci. */
object LegendarySpellLibrary {
    data class Definition(
        val id: String,
        val displayName: String,
        val fileName: String,
        val functionalPages: Int,
        val usagePages: Int,
        val links: List<String> = emptyList(),
    ) {
        val keyRoot: String get() = "item.hexlove.legendary_spell.$id"
        val nameKey: String get() = "$keyRoot.name"
        val messageKey: String get() = "$keyRoot.message"
    }

    private val definitions = listOf(
        Definition("frieren_flowers", "Frieren Flowers", "Frieren flowers.txt", 2, 2),
        Definition(
            "excavation_protocol",
            "Excavation Protocol",
            "Excavation Protocol.txt",
            2,
            3,
            listOf("https://youtu.be/h3D9MB5asyI?si=EYkgX1caLtgMkHNS"),
        ),
        Definition(
            "math_heart_outline",
            "Math Heart Outline",
            "Math Heart Outline.txt",
            2,
            2,
            listOf(
                "https://youtu.be/aEtwA-iwoCQ?si=lyYPe86gkXiGqFZ8",
                "https://youtu.be/qaKfndFxHLE?si=9XPS01kZYc8UqFny",
            ),
        ),
        Definition(
            "math_heart_solid_core",
            "Math Heart Solid Core",
            "Math Heart Solid Core.txt",
            2,
            2,
            listOf("https://youtu.be/uKbRXeJ9lv4?si=yPiRwbpCvaoSk5P3"),
        ),
        Definition("omnidirectional_spatial_plane", "Omnidirectional Spatial Plane", "Omnidirectional Spatial Plane.txt", 2, 2),
        Definition(
            "ethereal_sanctuary_sphere",
            "Ethereal Sanctuary Sphere",
            "Ethereal Sanctuary Sphere.txt",
            2,
            2,
            listOf("https://youtu.be/UHbmkxv-874?si=TuIqpB74Px27UmDk"),
        ),
        Definition(
            "illuminated_spiral_descent",
            "Illuminated Spiral Descent",
            "Illuminated Spiral Descent.txt",
            2,
            2,
            listOf("https://youtu.be/iE7Yst3a2nk?si=9eSxwiVS-7-UsWWm"),
        ),
        Definition(
            "blueprint_architect",
            "Blueprint Architect (Maker)",
            "Blueprint Architect (Maker).txt",
            2,
            4,
            listOf("https://youtu.be/ympFwEAUqc0?si=uWJQcEWSpwdzjYO6"),
        ),
        Definition(
            "blueprint_foreman",
            "Blueprint Foreman (Builder)",
            "Blueprint Foreman (Builder).txt",
            2,
            2,
            listOf("https://youtu.be/eTplxWaAD8o?si=V0RmFp6rnknFJCU3"),
        ),
        Definition(
            "elytra_catalyst",
            "Elytra Catalyst",
            "Elytra Catalyst.txt",
            2,
            2,
            listOf("https://youtu.be/2eOg5DoYuwU?si=c_gMaTnzU4Dx-aE4"),
        ),
    )

    private val patternLine = Regex(
        "^(EAST|WEST|NORTH_EAST|NORTH_WEST|SOUTH_EAST|SOUTH_WEST)\\s+([aqweds]+)$",
    )

    fun random(random: RandomSource): Definition = definitions[random.nextInt(definitions.size)]

    fun createFocus(definition: Definition): ItemStack {
        val focus = ItemStack(HexItems.FOCUS)
        focus.setHoverName(
            Component.translatable("item.hexlove.legendary_focus", Component.translatable(definition.nameKey))
                .withStyle(ChatFormatting.GOLD),
        )

        val holder = focus.item as IotaHolderItem
        holder.writeDatum(focus, ListIota(loadProgram(definition)))
        check(holder.readIotaTag(focus) != null) { "Legendary Focus ${definition.id} was not written" }
        (focus.item as ItemFocus).setVariant(focus, definitions.indexOf(definition) % ItemFocus.NUM_VARIANTS)
        ItemFocus.seal(focus)
        return focus
    }

    fun createMessage(definition: Definition): MutableComponent {
        val clickableLinks: Array<Any> = definition.links
            .map { link ->
                Component.literal(link).withStyle { style ->
                    style
                        .withUnderlined(true)
                        .withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, link))
                } as Any
            }
            .toTypedArray()
        return Component.translatable(definition.messageKey, *clickableLinks)
            .withStyle(ChatFormatting.GOLD)
    }

    fun createGrimoire(definition: Definition, recipient: Component): ItemStack {
        val book = ItemStack(Items.WRITTEN_BOOK)
        val tag = CompoundTag()
        tag.putString("title", definition.displayName)
        tag.putString("author", "@tteka_s / Hex: Amancy")
        tag.putBoolean("resolved", true)
        val pages = ListTag()
        val dedication = Component.empty()
            .append(
                Component.translatable("item.hexlove.author_grimoire.page.1", recipient)
                    .withStyle(ChatFormatting.DARK_PURPLE),
            )
            .append("\n\n")
            .append(
                Component.translatable(
                    "item.hexlove.author_grimoire.page.spell",
                    Component.translatable(definition.nameKey),
                ).withStyle(ChatFormatting.BLACK),
            )
        pages.add(StringTag.valueOf(Component.Serializer.toJson(dedication)))
        appendSection(
            pages,
            "item.hexlove.author_grimoire.section.usage",
            "${definition.keyRoot}.usage",
            definition.usagePages,
        )
        appendSection(
            pages,
            "item.hexlove.author_grimoire.section.functional",
            "${definition.keyRoot}.functional",
            definition.functionalPages,
        )
        tag.put("pages", pages)
        book.tag = tag
        return book
    }

    private fun appendSection(pages: ListTag, headingKey: String, textRoot: String, count: Int) {
        repeat(count) { index ->
            val page = Component.empty()
                .append(Component.translatable(headingKey).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD))
                .append("\n\n")
                .append(Component.translatable("$textRoot.${index + 1}").withStyle(ChatFormatting.BLACK))
            pages.add(StringTag.valueOf(Component.Serializer.toJson(page)))
        }
    }

    /** The bundled files never change while the game is running; parse each of them exactly once. */
    private val programs = HashMap<String, List<Iota>>()

    private fun loadProgram(definition: Definition): List<Iota> =
        synchronized(programs) { programs.getOrPut(definition.id) { readProgram(definition) } }

    private fun readProgram(definition: Definition): List<Iota> {
        val path = "assets/hexlove/spells/${definition.fileName}"
        val stream = checkNotNull(LegendarySpellLibrary::class.java.classLoader.getResourceAsStream(path)) {
            "Missing bundled spell resource $path"
        }
        return stream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.map(String::trim)
                .filter { it.isNotEmpty() && it != "[" && it != "]" }
                .map { line ->
                    val match = checkNotNull(patternLine.matchEntire(line)) {
                        "Invalid pattern line in ${definition.fileName}: $line"
                    }
                    val direction = HexDir.valueOf(match.groupValues[1])
                    // Exported focal-port programs may contain self-crossing patterns. Hex Casting's
                    // checked constructor rejects those and used to make that reward silently empty.
                    PatternIota(HexPattern.fromAnglesUnchecked(match.groupValues[2], direction))
                }
                .toList()
        }.also { check(it.isNotEmpty()) { "Legendary spell ${definition.id} is empty" } }
    }
}
