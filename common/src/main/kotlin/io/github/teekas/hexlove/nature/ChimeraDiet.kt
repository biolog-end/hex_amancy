package io.github.teekas.hexlove.nature

import io.github.teekas.hexlove.registry.HexloveTags
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** How a single food entry is mapped to its stats under the active nature. */
data class DietEntry(val nutrition: Int, val saturation: Float, val chewTicks: Int)

/** Result of checking a stack against the active nature's diet. */
enum class DietVerdict {
    /** Allowed by the diet table and vanilla food system, or plain food passing through. */
    ALLOWED_FOOD,
    /** In the diet tag but not vanilla food — requires the chew mechanic. */
    CHEWABLE,
    /** Food (isEdible = true) that is not in the active nature's diet — block eating. */
    FORBIDDEN,
    /** Not food and not in the diet tag — the nature system does not care about this item. */
    IRRELEVANT,
}

/**
 * Diet tables for both natures.
 *
 * Tag membership (loaded at runtime from JSON) controls *edibility*. The Kotlin table below
 * controls *numbers* for named items. Items in the tag but absent from the table fall back to
 * the nature's default entry.
 */
object ChimeraDiet {

    // ---- Herbivore: non-food items that become chewable ----

    private val HERBIVORE_CHEWABLE: Map<Item, DietEntry> = mapOf(
        Items.WHEAT               to DietEntry(3, 0.4f, 32),
        Items.GRASS               to DietEntry(1, 0.1f, 24),   // short grass (minecraft:grass in 1.20.1)
        Items.TALL_GRASS          to DietEntry(2, 0.1f, 32),
        Items.FERN                to DietEntry(1, 0.1f, 24),
        Items.LARGE_FERN          to DietEntry(1, 0.1f, 24),
        Items.WHEAT_SEEDS         to DietEntry(1, 0.2f, 20),
        Items.BEETROOT_SEEDS      to DietEntry(1, 0.2f, 20),
        Items.MELON_SEEDS         to DietEntry(1, 0.2f, 20),
        Items.PUMPKIN_SEEDS       to DietEntry(1, 0.2f, 20),
        Items.TORCHFLOWER_SEEDS   to DietEntry(1, 0.2f, 20),
        Items.PITCHER_POD         to DietEntry(1, 0.2f, 20),
        Items.SUGAR_CANE          to DietEntry(2, 0.2f, 24),
        Items.KELP                to DietEntry(1, 0.1f, 24),
        Items.SEAGRASS            to DietEntry(1, 0.1f, 24),
    )

    // ---- Herbivore: vanilla food items with boosted values ----

    private val HERBIVORE_BOOSTED: Map<Item, DietEntry> = mapOf(
        Items.POTATO          to DietEntry(4, 0.6f, 0),
        Items.CARROT          to DietEntry(5, 0.8f, 0),
        Items.BEETROOT        to DietEntry(4, 0.8f, 0),
        Items.APPLE           to DietEntry(5, 0.6f, 0),
        Items.MELON_SLICE     to DietEntry(4, 0.5f, 0),
        Items.SWEET_BERRIES   to DietEntry(4, 0.4f, 0),
        Items.GLOW_BERRIES    to DietEntry(4, 0.4f, 0),
        Items.DRIED_KELP      to DietEntry(2, 0.6f, 0),
    )

    // ---- Ghoul: mostly vanilla food with boosted values; one non-food ----

    private val GHOUL_BOOSTED: Map<Item, DietEntry> = mapOf(
        Items.ROTTEN_FLESH      to DietEntry(8, 0.8f, 0),
        Items.BEEF              to DietEntry(8, 0.8f, 0),
        Items.PORKCHOP          to DietEntry(8, 0.8f, 0),
        Items.MUTTON            to DietEntry(8, 0.8f, 0),
        Items.CHICKEN           to DietEntry(8, 0.8f, 0),
        Items.RABBIT            to DietEntry(8, 0.8f, 0),
        Items.COD               to DietEntry(6, 0.6f, 0),
        Items.SALMON            to DietEntry(6, 0.6f, 0),
        Items.TROPICAL_FISH     to DietEntry(6, 0.6f, 0),
        Items.PUFFERFISH        to DietEntry(6, 0.6f, 0),
        Items.SPIDER_EYE        to DietEntry(5, 0.8f, 0),
    )

    // bone is the one non-food chewable for the ferocious nature
    private val GHOUL_CHEWABLE: Map<Item, DietEntry> = mapOf(
        Items.BONE to DietEntry(2, 0.1f, 64),
    )

    // Fallback for items in the tag but missing from the named table.
    private val SERENE_FALLBACK = DietEntry(2, 0.2f, 32)
    private val FEROCIOUS_FALLBACK = DietEntry(4, 0.4f, 32)

    // All items that chew (non-food) across either nature, used by context-free mixin guards.
    private val ALL_CHEWABLE: Set<Item> by lazy {
        (HERBIVORE_CHEWABLE.keys + GHOUL_CHEWABLE.keys).toSet()
    }

    // ---- Public API ----

    /**
     * Returns the explicit table entry for [item] under [nature], or the nature's fallback if
     * the item appears in the tag but is not in the table.
     *
     * Returns null only when the item is not in the tag at all (caller should treat as FORBIDDEN).
     * This method does *not* do the tag lookup — callers must use [verdict] for full gate logic.
     */
    fun entry(nature: ChimeraNature, item: Item): DietEntry? = when (nature) {
        ChimeraNature.SERENE ->
            HERBIVORE_CHEWABLE[item] ?: HERBIVORE_BOOSTED[item]
        ChimeraNature.FEROCIOUS ->
            GHOUL_CHEWABLE[item] ?: GHOUL_BOOSTED[item]
    }

    /**
     * Full verdict for [stack] under [nature].
     *
     * Tag lookup happens here; [entry] returns numbers only.
     */
    fun verdict(nature: ChimeraNature, stack: ItemStack): DietVerdict {
        val tag = when (nature) {
            ChimeraNature.SERENE -> HexloveTags.HERBIVORE_DIET
            ChimeraNature.FEROCIOUS -> HexloveTags.GHOUL_DIET
        }
        val inTag = stack.`is`(tag)

        // The gate must only touch consumption. Snowballs, eggs, bows and hex staves are
        // "use" actions but not eating, so anything that is neither vanilla food nor a
        // chewable in our tag is not our business — we return null (represented here as
        // the sentinel FORBIDDEN only for actual food outside the diet).
        return when {
            stack.isEdible && inTag -> DietVerdict.ALLOWED_FOOD
            stack.isEdible          -> DietVerdict.FORBIDDEN         // real food, wrong diet
            inTag                    -> DietVerdict.CHEWABLE          // grass, bone, seeds…
            else                     -> DietVerdict.IRRELEVANT        // snowball, egg, staff — leave alone
        }
    }

    /** Returns true when [item] is in either nature's chew table.
     *  Used by context-free mixin guards that must not read player state. */
    fun isChewableByAnyNature(item: Item): Boolean = item in ALL_CHEWABLE

    /**
     * Returns the full entry for [item] under [nature], using the fallback when the item is in
     * the tag but absent from the table. Callers already confirmed the item is in the tag.
     */
    fun entryOrFallback(nature: ChimeraNature, item: Item): DietEntry =
        entry(nature, item) ?: when (nature) {
            ChimeraNature.SERENE -> SERENE_FALLBACK
            ChimeraNature.FEROCIOUS -> FEROCIOUS_FALLBACK
        }
}
