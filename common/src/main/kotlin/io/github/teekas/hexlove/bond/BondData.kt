package io.github.teekas.hexlove.bond

import net.minecraft.nbt.CompoundTag

/**
 * Container for every state a single entity can carry. Serialized under [NBT_KEY]; an empty
 * container is never written, so entities without love magic pay nothing.
 */
class BondData {
    var love: LoveBond? = null
    var jealousy: JealousyBond? = null
    var heartbreak: HeartbreakState? = null
    var siren: SirenState? = null
    var pheromone: PheromoneState? = null
    var phantomIdeal: PhantomIdealState? = null
    var courtship: CourtshipState? = null

    val isEmpty: Boolean
        get() = love == null && jealousy == null && heartbreak == null && siren == null &&
            pheromone == null && phantomIdeal == null && courtship == null

    fun copyFrom(other: BondData) {
        love = other.love
        jealousy = other.jealousy
        heartbreak = other.heartbreak
        siren = other.siren
        pheromone = other.pheromone
        phantomIdeal = other.phantomIdeal
        courtship = other.courtship
    }

    fun clear() {
        love = null
        jealousy = null
        heartbreak = null
        siren = null
        pheromone = null
        phantomIdeal = null
        courtship = null
    }

    fun save(): CompoundTag {
        val tag = CompoundTag()
        love?.let {
            tag.put("love", CompoundTag().apply {
                putUUID("target", it.target)
                putLong("expiresAt", it.expiresAt)
                putBoolean("permanent", it.permanent)
            })
        }
        jealousy?.let {
            tag.put("jealousy", CompoundTag().apply {
                putUUID("target", it.target)
                putLong("expiresAt", it.expiresAt)
            })
        }
        heartbreak?.let {
            tag.put("heartbreak", CompoundTag().apply {
                putLong("expiresAt", it.expiresAt)
            })
        }
        siren?.let {
            tag.put("siren", CompoundTag().apply {
                putUUID("caller", it.caller)
                putLong("expiresAt", it.expiresAt)
            })
        }
        pheromone?.let {
            tag.put("pheromone", CompoundTag().apply {
                putLong("expiresAt", it.expiresAt)
            })
        }
        phantomIdeal?.let {
            tag.put("phantomIdeal", CompoundTag().apply {
                putUUID("ideal", it.ideal)
                putLong("expiresAt", it.expiresAt)
            })
        }
        courtship?.let {
            tag.put("courtship", CompoundTag().apply {
                putUUID("mate", it.mate)
                putString("kind", it.kind.name)
                putInt("offspring", it.offspring)
                putLong("expiresAt", it.expiresAt)
                putBoolean("leader", it.leader)
                putInt("contactTicks", it.contactTicks)
            })
        }
        return tag
    }

    fun load(tag: CompoundTag) {
        clear()
        if (tag.contains("love")) {
            val sub = tag.getCompound("love")
            if (sub.hasUUID("target")) {
                love = LoveBond(sub.getUUID("target"), sub.getLong("expiresAt"), sub.getBoolean("permanent"))
            }
        }
        if (tag.contains("jealousy")) {
            val sub = tag.getCompound("jealousy")
            if (sub.hasUUID("target")) {
                jealousy = JealousyBond(sub.getUUID("target"), sub.getLong("expiresAt"))
            }
        }
        if (tag.contains("heartbreak")) {
            heartbreak = HeartbreakState(tag.getCompound("heartbreak").getLong("expiresAt"))
        }
        if (tag.contains("siren")) {
            val sub = tag.getCompound("siren")
            if (sub.hasUUID("caller")) {
                siren = SirenState(sub.getUUID("caller"), sub.getLong("expiresAt"))
            }
        }
        if (tag.contains("pheromone")) {
            pheromone = PheromoneState(tag.getCompound("pheromone").getLong("expiresAt"))
        }
        if (tag.contains("phantomIdeal")) {
            val sub = tag.getCompound("phantomIdeal")
            if (sub.hasUUID("ideal")) {
                phantomIdeal = PhantomIdealState(sub.getUUID("ideal"), sub.getLong("expiresAt"))
            }
        }
        if (tag.contains("courtship")) {
            val sub = tag.getCompound("courtship")
            val kind = runCatching { CourtshipKind.valueOf(sub.getString("kind")) }.getOrNull()
            if (sub.hasUUID("mate") && kind != null) {
                courtship = CourtshipState(
                    mate = sub.getUUID("mate"),
                    kind = kind,
                    offspring = sub.getInt("offspring"),
                    expiresAt = sub.getLong("expiresAt"),
                    leader = sub.getBoolean("leader"),
                    contactTicks = sub.getInt("contactTicks"),
                )
            }
        }
    }

    companion object {
        const val NBT_KEY = "hexlove:bonds"

        fun of(tag: CompoundTag): BondData = BondData().also { it.load(tag) }
    }
}
