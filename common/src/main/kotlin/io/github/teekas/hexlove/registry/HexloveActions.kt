package io.github.teekas.hexlove.registry

import at.petrak.hexcasting.api.casting.ActionRegistryEntry
import at.petrak.hexcasting.api.casting.castables.Action
import at.petrak.hexcasting.api.casting.math.HexDir
import at.petrak.hexcasting.api.casting.math.HexPattern
import at.petrak.hexcasting.common.lib.HexRegistries
import at.petrak.hexcasting.common.lib.hex.HexActions
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.casting.actions.spells.OpAnimalPassion
import io.github.teekas.hexlove.casting.actions.spells.OpBlindingJealousy
import io.github.teekas.hexlove.casting.actions.spells.OpBreathExchange
import io.github.teekas.hexlove.casting.actions.spells.OpCupidsCharm
import io.github.teekas.hexlove.casting.actions.spells.OpHeartbreak
import io.github.teekas.hexlove.casting.actions.spells.OpPhantomIdeal
import io.github.teekas.hexlove.casting.actions.spells.OpPheromoneMist
import io.github.teekas.hexlove.casting.actions.spells.OpHeartsPurification
import io.github.teekas.hexlove.casting.actions.spells.OpHeartsReflection
import io.github.teekas.hexlove.casting.actions.spells.OpMatchmakersPurification
import io.github.teekas.hexlove.casting.actions.spells.OpSirensSong
import io.github.teekas.hexlove.casting.actions.spells.OpSoulbind
import io.github.teekas.hexlove.casting.actions.spells.OpSuccubusTithe
import io.github.teekas.hexlove.casting.actions.spells.OpWeaversPurification

object HexloveActions : HexloveRegistrar<ActionRegistryEntry>(
    HexRegistries.ACTION,
    { HexActions.REGISTRY },
) {
    /**
     * Great Spell: the one irreversible thing this mod can do.
     *
     * A large heart run through by an arrow. Sixteen segments: the twelve-edge heart outline, entered
     * from the left along the shaft and left again through the middle of the heart and out the right.
     *
     * The scrungled per-world signature keeps this shape — [at.petrak.hexcasting.api.casting.math.EulerPathFinder]
     * only looks for a different *route* over the same edges — so what a player finds on an Ancient
     * Scroll is still a pierced heart, just drawn starting somewhere else.
     */
    val CUPIDS_CHARM = make("cupids_charm/great", HexDir.EAST, "aewdadwewwdwdww", OpCupidsCharm)

    /**
     * A second Great Spell: an ornate blood-vessel. Its wide outer bowl closes around a knotted
     * inner vein before the line escapes; a strong spell should look like it took time to write.
     */
    val SUCCUBUS_TITHE = make(
        "succubus_tithe/great",
        HexDir.NORTH_WEST,
        "weweewewwweeweweeeqqqqqdqqwqeqqqd",
        OpSuccubusTithe,
    )

    /**
     * [ANIMAL_PASSION]'s heart with the last stroke turned aside instead of closing the outline: the
     * question asked before there is a bond, drawn as a heart that is not finished yet. Eight segments.
     *
     * The old drawing was a plain hexagon, `qqqqq`, which is the shape of Hex's own Zero Vector; Hex
     * keys normal actions by their angle signature alone, so one of the two silently shadowed the other.
     */
    val MATCHMAKERS_PURIFICATION = make("matchmakers_purification", HexDir.NORTH_WEST, "wedadeq", OpMatchmakersPurification)

    /** The small heart, plain and closed — the same motif as the Great Spell, at a fraction of the size. */
    val ANIMAL_PASSION = make("animal_passion", HexDir.NORTH_WEST, "wedadew", OpAnimalPassion)

    /** A heart like Animal Passion's, with its final stroke unravelling into a doubled thread. */
    val BREATH_EXCHANGE = make("breath_exchange", HexDir.NORTH_WEST, "wedadewwqee", OpBreathExchange)

    /** Animal Passion's small heart enclosed by a six-sided circle: perfume held inside a boundary. */
    val PHEROMONE_MIST = make("pheromone_mist", HexDir.NORTH_WEST, "qqqqqwedadew", OpPheromoneMist)

    /** A heart opening into a long mirrored tail: an image that calls attention but cannot be held. */
    val PHANTOM_IDEAL = make("phantom_ideal", HexDir.SOUTH_WEST, "wedadewqeeqq", OpPhantomIdeal)

    /**
     * A jagged scribble that doubles back on itself twice and never closes: the shape of a thought
     * that cannot let go. The old eye was too tidy for what this spell does.
     */
    val BLINDING_JEALOUSY = make("blinding_jealousy", HexDir.EAST, "qaeeqad", OpBlindingJealousy)

    /** A six-pointed star drawn in one closed stroke: the web of threads it reads. */
    val WEAVERS_PURIFICATION = make("weavers_purification", HexDir.EAST, "dqdqdqdqdqd", OpWeaversPurification)

    /**
     * Weaver's entire star, followed by a line that enters its centre and writes a pair of tiny
     * joined triangles there. It reads the strength held inside the web, not the web itself.
     */
    val HEARTS_PURIFICATION = make("hearts_purification", HexDir.EAST, "dqdqdqdqdqdeeddwdd", OpHeartsPurification)

    /** The small heart again, with a crack driven straight through the middle of it. */
    val HEARTBREAK = make("heartbreak", HexDir.SOUTH_EAST, "deedadewd", OpHeartbreak)

    /** Two crests of a wave, travelling away from the singer. */
    val SIRENS_SONG = make("sirens_song", HexDir.NORTH_EAST, "eeqqee", OpSirensSong)

    /**
     * Animal Passion's whole heart in front, with the left half of another heart peeking out
     * behind it. The overlap is intentional: two souls, one vow, neither erased by the other.
     */
    val SOULBIND = make("soulbind", HexDir.WEST, "wedadqdadewdw", OpSoulbind)

    /** Animal Passion's heart reflected across its diagonal, so the partner answers from the other side. */
    val HEARTS_REFLECTION = make("hearts_reflection", HexDir.SOUTH_EAST, "adewdwe", OpHeartsReflection)

    /** Signatures that are ours, for the collision check at startup. */
    val signatures: Map<String, String> = mapOf(
        "cupids_charm/great" to "aewdadwewwdwdww",
        "succubus_tithe/great" to "weweewewwweeweweeeqqqqqdqqwqeqqqd",
        "matchmakers_purification" to "wedadeq",
        "animal_passion" to "wedadew",
        "breath_exchange" to "wedadewwqee",
        "pheromone_mist" to "qqqqqwedadew",
        "phantom_ideal" to "wedadewqeeqq",
        "blinding_jealousy" to "qaeeqad",
        "weavers_purification" to "dqdqdqdqdqd",
        "hearts_purification" to "dqdqdqdqdqdeeddwdd",
        "heartbreak" to "deedadewd",
        "sirens_song" to "eeqqee",
        "soulbind" to "wedadqdadewdw",
        "hearts_reflection" to "adewdwe",
    )

    private fun make(name: String, startDir: HexDir, signature: String, action: Action) =
        make(name, startDir, signature) { action }

    private fun make(name: String, startDir: HexDir, signature: String, getAction: () -> Action) = register(name) {
        ActionRegistryEntry(HexPattern.fromAngles(signature, startDir), getAction())
    }

    /**
     * Requirement 1.5: a clash with somebody else's pattern is logged with both names and the game
     * keeps loading. Refusing to start would be worse than one unreachable pattern.
     */
    fun checkSignatureCollisions() {
        val ours = signatures.entries.associate { (name, signature) -> signature to Hexlove.id(name) }
        for (entry in registry.entrySet()) {
            val theirs = entry.value.prototype.anglesSignature()
            val clash = ours[theirs] ?: continue
            if (entry.key.location() == clash) continue
            Hexlove.LOGGER.error(
                "Pattern signature collision: {} and {} both use '{}'. One of them will be unreachable.",
                clash, entry.key.location(), theirs,
            )
        }
    }
}
