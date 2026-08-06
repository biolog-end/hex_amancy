package io.github.teekas.hexlove.world

import io.github.teekas.hexlove.marriage.RingData
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/** Requirement 19.9: health owed by an entity. The creditor is bookkeeping only, it never gates collection. */
data class TitheDebt(val entity: UUID, val amount: Float, val creditor: UUID, val createdAt: Long)

/**
 * Requirement 19.17: the dimension is recorded so unloaded harem members can still be reached.
 * `lastKnownHealth` is refreshed while the creature is loaded; it is deliberately only a conservative
 * snapshot, never a promise that an unloaded target is still alive. `loveExpiresAt` is stored here
 * too, so an unloaded temporary charm can expire without being incorrectly used as a tithe source.
 */
data class HaremMember(
    val entity: UUID,
    val dimension: ResourceLocation,
    val lastKnownHealth: Float = 0f,
    val loveExpiresAt: Long = 0L,
) {
    fun isLoveActiveAt(gameTime: Long): Boolean = loveExpiresAt > gameTime
}

/**
 * Requirement (bugfix): the world link belongs to the soul, not to any single ring, so
 * unlinking one ring and attuning a fresh one cannot re-grant the first-attunement bonus,
 * and multiple rings on the same soul share one accumulation pool instead of forking it.
 * A ring only marks whether it currently displays that soul's bond; the ring's own NBT
 * charge value is a mirror of this record and is authoritative only when saved into a
 * chest with no online owner around to consult.
 */
data class SoulWorldMemory(val everAttuned: Boolean, val charge: Double) {
    fun withCharge(c: Double): SoulWorldMemory = copy(charge = c.coerceIn(0.0, RingData.MAX_WORLD_CHARGE))
    fun withEverAttuned(): SoulWorldMemory = if (everAttuned) this else copy(everAttuned = true)
}

data class MarriageRecord(
    val a: UUID,
    val b: UUID,
    val active: Boolean,
    val createdAt: Long,
    val colour: Int,
    val accentColour: Int,
) {
    fun other(who: UUID): UUID = if (who == a) b else a
    fun contains(who: UUID) = who == a || who == b
}

/**
 * World-level data (Requirement 3.10): debts, harems and marriages must be readable while the
 * entity or player in question is not loaded, so they cannot live on the entity.
 *
 * Stored on the overworld's storage so it survives unloading of any single dimension.
 */
class HexloveWorldData : SavedData() {
    val debts: MutableMap<UUID, TitheDebt> = HashMap()
    val harems: MutableMap<UUID, MutableSet<HaremMember>> = HashMap()
    val marriages: MutableMap<UUID, MarriageRecord> = HashMap()
    val soulWorldMemories: MutableMap<UUID, SoulWorldMemory> = HashMap()

    // ---- debts (Debt_Ledger) ----

    fun debtOf(entity: UUID): Float = debts[entity]?.amount ?: 0f

    fun addDebt(entity: UUID, amount: Float, creditor: UUID, now: Long) {
        if (amount <= 0f) return
        val existing = debts[entity]
        debts[entity] = TitheDebt(entity, (existing?.amount ?: 0f) + amount, creditor, existing?.createdAt ?: now)
        setDirty()
    }

    /** Removes and returns the debt *before* any damage is applied, so it can never be collected twice. */
    fun consumeDebt(entity: UUID): TitheDebt? = debts.remove(entity)?.also { setDirty() }

    fun dropDebt(entity: UUID) {
        if (debts.remove(entity) != null) setDirty()
    }

    // ---- harems (Harem_Registry) ----

    fun harem(owner: UUID): Set<HaremMember> = harems[owner] ?: emptySet()

    fun addHaremMember(owner: UUID, member: HaremMember) {
        val set = harems.getOrPut(owner) { LinkedHashSet() }
        val old = set.firstOrNull { it.entity == member.entity }
        if (old == member) return
        set.removeIf { it.entity == member.entity }
        set.add(member)
        setDirty()
    }

    fun removeHaremMember(owner: UUID, entity: UUID) {
        val set = harems[owner] ?: return
        if (set.removeIf { it.entity == entity }) {
            if (set.isEmpty()) harems.remove(owner)
            setDirty()
        }
    }

    /** Requirement 3.12: an entity that stops existing leaves every harem. */
    fun forgetEntity(entity: UUID) {
        var changed = false
        val it = harems.entries.iterator()
        while (it.hasNext()) {
            val (_, set) = it.next()
            if (set.removeIf { m -> m.entity == entity }) changed = true
            if (set.isEmpty()) it.remove()
        }
        if (changed) setDirty()
    }

    // ---- marriages ----

    fun marriageOf(player: UUID): MarriageRecord? = marriages[player]

    /** A player has exactly one record slot, so this is the authoritative one-marriage guard. */
    fun createMarriage(a: UUID, b: UUID, now: Long, colour: Int, accentColour: Int): Boolean {
        if (a == b || marriages.containsKey(a) || marriages.containsKey(b)) return false
        val record = MarriageRecord(a, b, true, now, colour, accentColour)
        marriages[a] = record
        marriages[b] = record
        setDirty()
        return true
    }

    fun setMarriageActive(player: UUID, active: Boolean) {
        val record = marriages[player] ?: return
        if (record.active == active) return
        val updated = record.copy(active = active)
        marriages[record.a] = updated
        marriages[record.b] = updated
        setDirty()
    }

    /** Removes both indices atomically. Rings are cleaned separately, including on future logins. */
    fun removeMarriage(player: UUID): MarriageRecord? {
        val record = marriages[player] ?: return null
        marriages.remove(record.a)
        marriages.remove(record.b)
        setDirty()
        return record
    }

    // ---- soul world memory (attunement history + charge pool) ----

    fun soulMemoryOf(owner: UUID): SoulWorldMemory? = soulWorldMemories[owner]

    /** Does the soul remember ever being attuned? Once true, this never becomes false. */
    fun hasSoulEverAttuned(owner: UUID): Boolean = soulWorldMemories[owner]?.everAttuned == true

    fun markSoulAttuned(owner: UUID) {
        val existing = soulWorldMemories[owner]
        if (existing?.everAttuned == true) return
        soulWorldMemories[owner] = (existing ?: SoulWorldMemory(false, 0.0)).withEverAttuned()
        setDirty()
    }

    fun soulWorldCharge(owner: UUID): Double = soulWorldMemories[owner]?.charge ?: 0.0

    /** Adds [amount] to the soul's shared pool; returns how much was actually gained after the cap. */
    fun addSoulWorldCharge(owner: UUID, amount: Double): Double {
        if (amount <= 0.0) return 0.0
        val existing = soulWorldMemories[owner] ?: SoulWorldMemory(false, 0.0)
        val before = existing.charge
        val after = (before + amount).coerceIn(0.0, RingData.MAX_WORLD_CHARGE)
        if (after == before) return 0.0
        soulWorldMemories[owner] = existing.withCharge(after)
        setDirty()
        return after - before
    }

    fun spendSoulWorldCharge(owner: UUID, wholeUnits: Int): Boolean {
        if (wholeUnits <= 0) return false
        val existing = soulWorldMemories[owner] ?: return false
        if (existing.charge + 1.0e-7 < wholeUnits.toDouble()) return false
        soulWorldMemories[owner] = existing.withCharge((existing.charge - wholeUnits).coerceAtLeast(0.0))
        setDirty()
        return true
    }

    /**
     * One-shot migration of legacy per-ring state into the soul pool. Safe to call every time
     * a ring is presented at an altar -- it copies non-zero legacy history/charge in only when
     * the soul has no record yet, so it never doubles anything.
     */
    fun migrateLegacyRing(owner: UUID, legacyAttuned: Boolean, legacyCharge: Double) {
        val existing = soulWorldMemories[owner]
        val everAttuned = (existing?.everAttuned == true) || legacyAttuned || legacyCharge > 0.0
        val charge = maxOf(existing?.charge ?: 0.0, legacyCharge.coerceIn(0.0, RingData.MAX_WORLD_CHARGE))
        if (existing != null && existing.everAttuned == everAttuned && existing.charge == charge) return
        soulWorldMemories[owner] = SoulWorldMemory(everAttuned, charge)
        setDirty()
    }

    // ---- serialization ----

    override fun save(tag: CompoundTag): CompoundTag {
        val debtList = ListTag()
        for (debt in debts.values) {
            debtList.add(CompoundTag().apply {
                putUUID("entity", debt.entity)
                putFloat("amount", debt.amount)
                putUUID("creditor", debt.creditor)
                putLong("createdAt", debt.createdAt)
            })
        }
        tag.put("debts", debtList)

        val haremList = ListTag()
        for ((owner, members) in harems) {
            if (members.isEmpty()) continue
            haremList.add(CompoundTag().apply {
                putUUID("player", owner)
                put("members", ListTag().also { list ->
                    for (member in members) {
                        list.add(CompoundTag().apply {
                            putUUID("entity", member.entity)
                            putString("dimension", member.dimension.toString())
                            putFloat("health", member.lastKnownHealth)
                            putLong("loveExpiresAt", member.loveExpiresAt)
                        })
                    }
                })
            })
        }
        tag.put("harems", haremList)

        val marriageList = ListTag()
        for (record in marriages.values.distinct()) {
            marriageList.add(CompoundTag().apply {
                putUUID("a", record.a)
                putUUID("b", record.b)
                putBoolean("active", record.active)
                putLong("createdAt", record.createdAt)
                putInt("colour", record.colour)
                putInt("accentColour", record.accentColour)
            })
        }
        tag.put("marriages", marriageList)

        val soulList = ListTag()
        for ((owner, memory) in soulWorldMemories) {
            if (!memory.everAttuned && memory.charge <= 0.0) continue
            soulList.add(CompoundTag().apply {
                putUUID("owner", owner)
                putBoolean("everAttuned", memory.everAttuned)
                putDouble("charge", memory.charge)
            })
        }
        tag.put("soulWorld", soulList)

        return tag
    }

    companion object {
        const val FILE_NAME = "hexlove_world"

        fun load(tag: CompoundTag): HexloveWorldData {
            val data = HexloveWorldData()

            for (element in tag.getList("debts", Tag.TAG_COMPOUND.toInt())) {
                val sub = element as CompoundTag
                if (!sub.hasUUID("entity")) continue
                val uuid = sub.getUUID("entity")
                val creditor = if (sub.hasUUID("creditor")) sub.getUUID("creditor") else uuid
                data.debts[uuid] = TitheDebt(uuid, sub.getFloat("amount"), creditor, sub.getLong("createdAt"))
            }

            for (element in tag.getList("harems", Tag.TAG_COMPOUND.toInt())) {
                val sub = element as CompoundTag
                if (!sub.hasUUID("player")) continue
                val members = LinkedHashSet<HaremMember>()
                for (memberTag in sub.getList("members", Tag.TAG_COMPOUND.toInt())) {
                    val m = memberTag as CompoundTag
                    if (!m.hasUUID("entity")) continue
                    val dimension = ResourceLocation.tryParse(m.getString("dimension")) ?: continue
                    members.add(HaremMember(
                        m.getUUID("entity"),
                        dimension,
                        if (m.contains("health", Tag.TAG_FLOAT.toInt())) m.getFloat("health") else 0f,
                        if (m.contains("loveExpiresAt", Tag.TAG_LONG.toInt())) m.getLong("loveExpiresAt") else 0L,
                    ))
                }
                if (members.isNotEmpty()) data.harems[sub.getUUID("player")] = members
            }

            for (element in tag.getList("soulWorld", Tag.TAG_COMPOUND.toInt())) {
                val sub = element as CompoundTag
                if (!sub.hasUUID("owner")) continue
                val charge = sub.getDouble("charge").coerceIn(0.0, RingData.MAX_WORLD_CHARGE)
                val attuned = sub.getBoolean("everAttuned") || charge > 0.0
                data.soulWorldMemories[sub.getUUID("owner")] = SoulWorldMemory(attuned, charge)
            }

            for (element in tag.getList("marriages", Tag.TAG_COMPOUND.toInt())) {
                val sub = element as CompoundTag
                if (!sub.hasUUID("a") || !sub.hasUUID("b")) continue
                val a = sub.getUUID("a")
                val b = sub.getUUID("b")
                val record = MarriageRecord(
                    a,
                    b,
                    sub.getBoolean("active"),
                    sub.getLong("createdAt"),
                    if (sub.contains("colour", Tag.TAG_INT.toInt())) sub.getInt("colour") else RingData.fallbackPairAura(a, b),
                    if (sub.contains("accentColour", Tag.TAG_INT.toInt())) {
                        sub.getInt("accentColour")
                    } else {
                        RingData.companionAura(if (sub.contains("colour", Tag.TAG_INT.toInt())) sub.getInt("colour") else RingData.fallbackPairAura(a, b))
                    },
                )
                data.marriages[record.a] = record
                data.marriages[record.b] = record
            }

            return data
        }

        fun get(server: MinecraftServer): HexloveWorldData =
            server.overworld().dataStorage.computeIfAbsent(::load, ::HexloveWorldData, FILE_NAME)
    }
}
