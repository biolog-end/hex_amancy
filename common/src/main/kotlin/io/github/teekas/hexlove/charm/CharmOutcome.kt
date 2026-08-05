package io.github.teekas.hexlove.charm

import io.github.teekas.hexlove.config.HexloveServerConfig

/** Requirement 7.9: exactly one of these, always, never an exception. */
sealed interface CharmOutcome {
    /**
     * Requirements 10.1-10.3, 18.4, 7.13.
     *
     * [onMaster] says which of the two the caster has to be told about: a broken heart on the side of
     * the beloved is a different sentence from a broken heart on the side of the lover.
     */
    data class Immune(val reason: ImmuneReason, val onMaster: Boolean = false) : CharmOutcome

    /** Requirement 9.2: charming players switched off by the server. */
    data object PlayerCharmDisallowed : CharmOutcome

    /** Requirement 11: two food-breedable animals of the same species. */
    data object Breed : CharmOutcome

    /** Requirement 12: two food-breedable animals of different species. */
    data object Chimera : CharmOutcome

    /** Requirement 13: zombie + zombie. */
    data object ZombieBreed : CharmOutcome

    /** Requirement 13: zombie villager + zombie villager. */
    data object ZombieVillagerBreed : CharmOutcome

    /** Requirement 9: temporary bond on a player. */
    data object CharmPlayer : CharmOutcome

    /** Requirement 8: pet behavior on any mob. */
    data object CharmMob : CharmOutcome
}

enum class ImmuneReason {
    TAG,

    /** A boss: recognised by the sheer size of its heart, and refused at any strength. */
    BEYOND_LOVE,

    /** Not a boss, but more than *this* strength of feeling can hold. More would do. */
    OUT_OF_REACH,

    INVULNERABLE,
    HEARTBROKEN,
    SELF,
    DISABLED_BREEDING,
}

/** Extra rules the outcome table consults; kept separate so the table stays pure. */
data class CharmRules(
    val reach: CharmReach,
    val charmPlayers: Boolean,
    val allowChimera: Boolean,
    val allowZombieBreeding: Boolean,
    val allowZombieVillagerBreeding: Boolean,
) {
    companion object {
        fun fromConfig(cfg: HexloveServerConfig.ServerConfig = HexloveServerConfig.config) = CharmRules(
            reach = CharmReach.fromConfig(cfg),
            charmPlayers = cfg.toggles.charmPlayers,
            allowChimera = cfg.toggles.allowChimera,
            allowZombieBreeding = cfg.toggles.allowZombieBreeding,
            allowZombieVillagerBreeding = cfg.toggles.allowZombieVillagerBreeding,
        )
    }
}

object CharmImmunity {
    /**
     * Requirement 10.5: tag, then the boss threshold, then what this much feeling can reach, then the
     * invulnerability flag. In that order — the caster should hear the permanent reason before the
     * one they could fix by pouring in more.
     *
     * Players are exempt from both health rules: a player's heart resists as a fixed thing.
     */
    fun reasonFor(facts: TargetFacts, rules: CharmRules, power: Double): ImmuneReason? = when {
        facts.charmImmuneTag -> ImmuneReason.TAG
        facts.isPlayer -> if (facts.isInvulnerable) ImmuneReason.INVULNERABLE else null
        rules.reach.isBeyondLove(facts.maxHealth) -> ImmuneReason.BEYOND_LOVE
        facts.maxHealth > rules.reach.charmableHealth(power) -> ImmuneReason.OUT_OF_REACH
        facts.isInvulnerable -> ImmuneReason.INVULNERABLE
        else -> null
    }
}

object CharmOutcomes {
    /**
     * The outcome table of Requirement 7, checked strictly top to bottom.
     * Mixed pairs (cow + zombie, zombie + zombie villager) are not errors: the cow simply falls in love.
     */
    fun resolve(
        subject: TargetFacts,
        master: TargetFacts,
        sameEntity: Boolean,
        rules: CharmRules,
        power: Double,
    ): CharmOutcome {
        // checked first because it is the one mistake the caster made rather than a property of the target
        if (sameEntity) return CharmOutcome.Immune(ImmuneReason.SELF)
        CharmImmunity.reasonFor(subject, rules, power)?.let { return CharmOutcome.Immune(it) }

        // Requirement 18, revised: only the one who has to *feel* something needs an intact heart.
        // Being loved asks nothing of the beloved, so a heartbroken player can still be adored.
        if (subject.heartbroken) return CharmOutcome.Immune(ImmuneReason.HEARTBROKEN)

        val bothFoodBreedable = subject.breedCategory == BreedCategory.FOOD_BREEDABLE &&
            master.breedCategory == BreedCategory.FOOD_BREEDABLE

        // A child can love, but it cannot consent to a courtship. In particular, never route a
        // juvenile animal through CourtshipManager: clearing a breeding cooldown with age = 0 also
        // grows a baby instantly because Minecraft stores childhood as a negative age.
        if (bothFoodBreedable && (subject.isBaby || master.isBaby)) {
            return CharmOutcome.CharmMob
        }

        // a courtship, unlike a charm, needs both hearts: the other one has to answer
        val union = bothFoodBreedable ||
            (subject.breedCategory == BreedCategory.ZOMBIE && master.breedCategory == BreedCategory.ZOMBIE) ||
            (subject.breedCategory == BreedCategory.ZOMBIE_VILLAGER &&
                master.breedCategory == BreedCategory.ZOMBIE_VILLAGER)
        if (union && master.heartbroken) {
            return CharmOutcome.Immune(ImmuneReason.HEARTBROKEN, onMaster = true)
        }

        if (bothFoodBreedable) {
            return if (subject.speciesId == master.speciesId) {
                CharmOutcome.Breed
            } else if (rules.allowChimera) {
                CharmOutcome.Chimera
            } else {
                CharmOutcome.Immune(ImmuneReason.DISABLED_BREEDING)
            }
        }

        if (subject.breedCategory == BreedCategory.ZOMBIE && master.breedCategory == BreedCategory.ZOMBIE) {
            return if (rules.allowZombieBreeding) {
                CharmOutcome.ZombieBreed
            } else {
                CharmOutcome.Immune(ImmuneReason.DISABLED_BREEDING)
            }
        }

        if (subject.breedCategory == BreedCategory.ZOMBIE_VILLAGER &&
            master.breedCategory == BreedCategory.ZOMBIE_VILLAGER
        ) {
            return if (rules.allowZombieVillagerBreeding) {
                CharmOutcome.ZombieVillagerBreed
            } else {
                CharmOutcome.Immune(ImmuneReason.DISABLED_BREEDING)
            }
        }

        if (subject.isPlayer) {
            return if (rules.charmPlayers) CharmOutcome.CharmPlayer else CharmOutcome.PlayerCharmDisallowed
        }

        return CharmOutcome.CharmMob
    }
}
