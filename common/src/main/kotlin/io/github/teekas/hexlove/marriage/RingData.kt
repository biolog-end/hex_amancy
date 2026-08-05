package io.github.teekas.hexlove.marriage

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.PI
import kotlin.math.sin

/**
 * The small, portable half of a marriage record. World data is authoritative for the union itself;
 * this NBT makes a ring self-describing even while it lies on the ground or in a chest.
 */
object RingData {
    private const val BOUND = "hexlove:bound"
    private const val SPOUSE = "hexlove:spouse"
    private const val BOUND_NAME = "hexlove:bound_name"
    private const val SPOUSE_NAME = "hexlove:spouse_name"
    private const val SOUL_AURA = "hexlove:soul_aura"
    private const val PAIR_AURA = "hexlove:pair_aura"
    private const val PAIR_AURA_OTHER = "hexlove:pair_aura_other"
    private const val WORLD_SOUL = "hexlove:world_soul"
    private const val WORLD_ATTUNED_ONCE = "hexlove:world_attuned_once"
    private const val WORLD_CHARGE = "hexlove:world_charge"

    fun boundTo(stack: ItemStack): UUID? = stack.tag?.takeIf { it.hasUUID(BOUND) }?.getUUID(BOUND)

    fun spouse(stack: ItemStack): UUID? = stack.tag?.takeIf { it.hasUUID(SPOUSE) }?.getUUID(SPOUSE)

    /** Names travel with the ring, so a client tooltip never depends on a server-side profile cache. */
    fun boundName(stack: ItemStack): String? = stack.tag?.getString(BOUND_NAME)?.takeIf(String::isNotBlank)

    fun spouseName(stack: ItemStack): String? = stack.tag?.getString(SPOUSE_NAME)?.takeIf(String::isNotBlank)

    fun isBound(stack: ItemStack): Boolean = stack.tag?.hasUUID(BOUND) == true

    fun isMarried(stack: ItemStack): Boolean {
        val tag = stack.tag ?: return false
        return tag.hasUUID(BOUND) && tag.hasUUID(SPOUSE)
    }

    /** The altar's bond is deliberately separate from marriage and survives a vow or separation. */
    fun worldSoul(stack: ItemStack): UUID? =
        stack.tag?.takeIf { it.hasUUID(WORLD_SOUL) }?.getUUID(WORLD_SOUL)

    fun isWorldLinked(stack: ItemStack, owner: UUID? = null): Boolean {
        val linked = worldSoul(stack) ?: return false
        return owner == null || linked == owner
    }

    /** Remains true after severing so reconnecting the same soul cannot repeat the first blessing. */
    fun hasWorldAttunementHistory(stack: ItemStack): Boolean {
        val tag = stack.tag ?: return false
        // A linked ring from a save made before this marker existed has necessarily been attuned.
        return tag.getBoolean(WORLD_ATTUNED_ONCE) || tag.hasUUID(WORLD_SOUL)
    }

    fun worldCharge(stack: ItemStack): Double =
        stack.tag?.getDouble(WORLD_CHARGE)?.coerceIn(0.0, MAX_WORLD_CHARGE) ?: 0.0

    fun linkToWorld(stack: ItemStack, owner: UUID): Boolean {
        if (boundTo(stack) != owner || isWorldLinked(stack)) return false
        val tag = stack.orCreateTag
        tag.putUUID(WORLD_SOUL, owner)
        tag.putBoolean(WORLD_ATTUNED_ONCE, true)
        tag.putDouble(WORLD_CHARGE, worldCharge(stack))
        return true
    }

    /** Severs only the active world link; earned affection and attunement history stay with the soul. */
    fun unlinkFromWorld(stack: ItemStack, owner: UUID): Boolean {
        val tag = stack.tag ?: return false
        if (!tag.hasUUID(WORLD_SOUL) || tag.getUUID(WORLD_SOUL) != owner) return false
        tag.putBoolean(WORLD_ATTUNED_ONCE, true)
        tag.remove(WORLD_SOUL)
        return true
    }

    fun addWorldCharge(stack: ItemStack, amount: Double): Double {
        if (amount <= 0.0 || worldSoul(stack) == null) return 0.0
        val before = worldCharge(stack)
        val after = (before + amount).coerceIn(0.0, MAX_WORLD_CHARGE)
        if (after != before) stack.orCreateTag.putDouble(WORLD_CHARGE, after)
        return after - before
    }

    fun spendWorldCharge(stack: ItemStack, wholeUnits: Int): Boolean {
        if (wholeUnits <= 0) return false
        val before = worldCharge(stack)
        if (before + 1.0e-7 < wholeUnits.toDouble()) return false
        stack.orCreateTag.putDouble(WORLD_CHARGE, (before - wholeUnits).coerceAtLeast(0.0))
        return true
    }

    fun rememberOwnerName(stack: ItemStack, ownerName: String): Boolean =
        ownerName.takeIf(String::isNotBlank)?.let { putStringIfChanged(stack.orCreateTag, BOUND_NAME, it) } == true

    fun bind(stack: ItemStack, owner: UUID, ownerName: String? = null) {
        val tag = stack.orCreateTag
        val previousOwner = boundTo(stack)
        putUuidIfChanged(tag, BOUND, owner)
        ownerName?.let { rememberOwnerName(stack, it) }
        // A bound ring can never silently carry over a spouse from an old ritual.
        tag.remove(SPOUSE)
        tag.remove(SPOUSE_NAME)
        tag.remove(PAIR_AURA)
        tag.remove(PAIR_AURA_OTHER)
        if (previousOwner != owner) {
            tag.remove(WORLD_SOUL)
            tag.remove(WORLD_ATTUNED_ONCE)
            tag.remove(WORLD_CHARGE)
        }
        if (!tag.contains(SOUL_AURA)) tag.putInt(SOUL_AURA, randomAura())
    }

    fun marry(
        stack: ItemStack,
        owner: UUID,
        partner: UUID,
        pairAura: Int,
        pairAuraOther: Int = companionAura(pairAura),
        ownerName: String? = null,
        partnerName: String? = null,
    ): Boolean {
        val tag = stack.orCreateTag
        var changed = putUuidIfChanged(tag, BOUND, owner)
        changed = putUuidIfChanged(tag, SPOUSE, partner) || changed
        changed = (ownerName?.let { rememberOwnerName(stack, it) } == true) || changed
        changed = (partnerName?.takeIf(String::isNotBlank)?.let {
            putStringIfChanged(tag, SPOUSE_NAME, it)
        } == true) || changed
        if (!tag.contains(SOUL_AURA)) {
            tag.putInt(SOUL_AURA, randomAura())
            changed = true
        }
        changed = putIntIfChanged(tag, PAIR_AURA, pairAura) || changed
        changed = putIntIfChanged(tag, PAIR_AURA_OTHER, pairAuraOther) || changed
        return changed
    }

    /** Breaks only the marriage half of a ring; the soul it was bound to remains intact. */
    fun release(stack: ItemStack): Boolean {
        val tag = stack.tag ?: return false
        if (!tag.contains(SPOUSE) && !tag.contains(SPOUSE_NAME) &&
            !tag.contains(PAIR_AURA) && !tag.contains(PAIR_AURA_OTHER)
        ) return false
        tag.remove(SPOUSE)
        tag.remove(SPOUSE_NAME)
        tag.remove(PAIR_AURA)
        tag.remove(PAIR_AURA_OTHER)
        return true
    }

    fun matches(stack: ItemStack, owner: UUID, partner: UUID): Boolean =
        boundTo(stack) == owner && spouse(stack) == partner

    /** The first end of the ring's animated light gradient. */
    fun gradientStart(stack: ItemStack): Int = (gradientColours(stack) ushr 32).toInt()

    /** The second end of the ring's animated light gradient. */
    fun gradientEnd(stack: ItemStack): Int = gradientColours(stack).toInt()

    /** The stable colour used for tooltips and particles: the first end of the visible gradient. */
    fun auraColor(stack: ItemStack): Int = gradientStart(stack)

    /**
     * Gives the four non-overlapping active facets a moving sweep from one end of the gradient to
     * the other. The phase is client-local visual polish only; the two rings still share both ends.
     */
    fun animatedGradientColor(stack: ItemStack, facet: Int, millis: Long): Int {
        val phase = ((millis % GRADIENT_PERIOD_MILLIS).toDouble() / GRADIENT_PERIOD_MILLIS + facet / FACET_COUNT.toDouble()) % 1.0
        val amount = (sin(phase * 2.0 * PI) + 1.0) * 0.5
        val colours = gradientColours(stack)
        return blend((colours ushr 32).toInt(), colours.toInt(), amount)
    }

    /** The colour a ring had before its vow; the altar joins these two colours into one gradient. */
    fun soulAura(stack: ItemStack): Int {
        val tag = stack.tag ?: return 0xFFFFFF
        if (!tag.hasUUID(BOUND)) return 0xFFFFFF
        return soulAura(tag)
    }

    private fun soulAura(tag: CompoundTag): Int {
        if (tag.contains(SOUL_AURA)) return tag.getInt(SOUL_AURA)
        return fallbackSoulAura(tag.getUUID(BOUND))
    }

    /**
     * Packs both colours into one primitive value. Item tinting asks for four facets every frame;
     * one shared NBT walk avoids repeatedly materialising UUIDs just to test whether fields exist.
     */
    private fun gradientColours(stack: ItemStack): Long {
        val tag = stack.tag
        if (tag == null || !tag.hasUUID(BOUND)) return packColours(0xFFFFFF, 0xFFFFFF)

        val married = tag.hasUUID(SPOUSE)
        val soul = if (!married || !tag.contains(PAIR_AURA) || !tag.contains(PAIR_AURA_OTHER)) {
            soulAura(tag)
        } else {
            0
        }
        val start = if (married && tag.contains(PAIR_AURA)) tag.getInt(PAIR_AURA) else soul
        val end = when {
            married && tag.contains(PAIR_AURA_OTHER) -> tag.getInt(PAIR_AURA_OTHER)
            married && tag.contains(PAIR_AURA) -> companionAura(start)
            else -> companionAura(soul)
        }
        return packColours(start, end)
    }

    private fun packColours(start: Int, end: Int): Long =
        (start.toLong() shl 32) or (end.toLong() and 0xffffffffL)

    private fun putUuidIfChanged(tag: CompoundTag, key: String, value: UUID): Boolean {
        if (tag.hasUUID(key) && tag.getUUID(key) == value) return false
        tag.putUUID(key, value)
        return true
    }

    private fun putStringIfChanged(tag: CompoundTag, key: String, value: String): Boolean {
        if (tag.contains(key) && tag.getString(key) == value) return false
        tag.putString(key, value)
        return true
    }

    private fun putIntIfChanged(tag: CompoundTag, key: String, value: Int): Boolean {
        if (tag.contains(key, Tag.TAG_INT.toInt()) && tag.getInt(key) == value) return false
        tag.putInt(key, value)
        return true
    }

    /** Used by the altar when a new vow is made. The value is persisted in world data and both rings. */
    fun randomAura(): Int = Mth.hsvToRgb(ThreadLocalRandom.current().nextFloat(), 0.72f, 1.0f)

    /** A vivid but distinct partner for an unwed soul's two-colour shimmer. */
    fun companionAura(colour: Int): Int {
        val red = colour shr 16 and 0xff
        val green = colour shr 8 and 0xff
        val blue = colour and 0xff
        return blue shl 16 or red shl 8 or green
    }

    private fun blend(first: Int, second: Int, amount: Double): Int {
        val t = amount.coerceIn(0.0, 1.0)
        fun channel(shift: Int): Int {
            val a = first shr shift and 0xff
            val b = second shr shift and 0xff
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return channel(16) shl 16 or channel(8) shl 8 or channel(0)
    }

    /** Migration fallback for rings and marriage records created before a colour was persisted. */
    fun fallbackPairAura(a: UUID, b: UUID): Int {
        val low = if (compare(a, b) <= 0) a else b
        val high = if (compare(a, b) <= 0) b else a
        val mixed = low.mostSignificantBits xor low.leastSignificantBits xor
            high.mostSignificantBits xor high.leastSignificantBits
        return Mth.hsvToRgb(((mixed ushr 1) % 360L).toFloat() / 360f, 0.72f, 1.0f)
    }

    private fun fallbackSoulAura(owner: UUID): Int {
        val mixed = owner.mostSignificantBits xor owner.leastSignificantBits
        return Mth.hsvToRgb(((mixed ushr 1) % 360L).toFloat() / 360f, 0.72f, 1.0f)
    }

    private fun compare(a: UUID, b: UUID): Int {
        val most = a.mostSignificantBits.compareTo(b.mostSignificantBits)
        return if (most != 0) most else a.leastSignificantBits.compareTo(b.leastSignificantBits)
    }

    private const val GRADIENT_PERIOD_MILLIS = 1_800L
    private const val FACET_COUNT = 4
    const val MAX_WORLD_CHARGE = 64.0
}
