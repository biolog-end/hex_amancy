package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.asActionResult
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.getDouble
import at.petrak.hexcasting.api.casting.getEntity
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.bond.CourtshipKind
import io.github.teekas.hexlove.bond.LoveBond
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.casting.InfatuationSpellAction
import io.github.teekas.hexlove.casting.HexloveMishaps
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.charm.BreedingConfig
import io.github.teekas.hexlove.charm.BreedingExecutor
import io.github.teekas.hexlove.charm.BreedingPlanner
import io.github.teekas.hexlove.charm.BreedingSupport
import io.github.teekas.hexlove.charm.CharmConfig
import io.github.teekas.hexlove.charm.CharmModel
import io.github.teekas.hexlove.charm.CharmOutcome
import io.github.teekas.hexlove.charm.CharmOutcomes
import io.github.teekas.hexlove.charm.CharmResult
import io.github.teekas.hexlove.charm.CharmRules
import io.github.teekas.hexlove.charm.CourtshipManager
import io.github.teekas.hexlove.charm.ImmuneReason
import io.github.teekas.hexlove.fx.BondFx
import io.github.teekas.hexlove.marriage.MarriageManager
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.server.level.ServerPlayer

/**
 * Requirement 7: the Great Spell of the addon. Three iotas: who falls in love, with whom, and how
 * hard. The third one is a *strength of feeling*, not a duration — how long it lasts comes out of
 * the fight between that strength and the stubbornness of the target's heart.
 *
 * Ordinary creatures give way to a strength between one and five. Past five the price climbs
 * geometrically, which is what makes a boss possible in principle and absurd in practice.
 */
object OpCupidsCharm : InfatuationSpellAction {
    override val argc = 3

    override fun resultStack(result: SpellAction.Result): List<Iota> {
        val spell = result.effect as? Spell ?: return emptyList()
        return spell.durationSeconds?.asActionResult ?: emptyList()
    }

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result =
        ActionGuard.guard("cupids_charm") {
            val subject = args.getEntity(0, argc)
            val master = args.getEntity(1, argc)
            val power = args.getDouble(2, argc)

            // Requirement 7.8, revised: nothing to give is its own failure, with its own words
            if (!CharmModel.isValidPower(power)) {
                throw HexloveMishaps.noFeeling(args[2], 0)
            }

            env.assertEntityInRange(subject)
            env.assertEntityInRange(master)

            // A marriage does not make a player generally immune; it makes their heart answerable
            // to one named person. A charm towards that person remains a valid (if redundant) spell.
            val marriedSubject = subject as? net.minecraft.world.entity.player.Player
            val server = subject.level().server
            if (marriedSubject != null && server != null && !MarriageManager.permitsAffection(server, marriedSubject, master.uuid)) {
                val spouse = MarriageManager.spouseOf(server, subject.uuid)?.let(server.playerList::getPlayer)
                throw HexloveMishaps.boundHeart(subject, spouse)
            }

            val rules = CharmRules.fromConfig()
            val outcome = CharmOutcomes.resolve(
                subject = BreedingSupport.facts(subject),
                master = BreedingSupport.facts(master),
                sameEntity = subject === master,
                rules = rules,
                power = power,
            )

            when (outcome) {
                // Requirements 7.13, 10, 18, revised: every refusal says what it actually is
                is CharmOutcome.Immune -> {
                    val who = if (outcome.onMaster) master else subject
                    throw when (outcome.reason) {
                        ImmuneReason.SELF -> HexloveMishaps.selfLove(subject)
                        ImmuneReason.HEARTBROKEN -> HexloveMishaps.brokenHeart(who, beloved = outcome.onMaster)
                        ImmuneReason.BEYOND_LOVE -> HexloveMishaps.tooStrong(who)
                        ImmuneReason.OUT_OF_REACH -> HexloveMishaps.outOfReach(who)
                        ImmuneReason.DISABLED_BREEDING -> HexloveMishaps.forbiddenUnion(subject, master)
                        ImmuneReason.TAG, ImmuneReason.INVULNERABLE -> HexloveMishaps.immune(who)
                    }
                }

                CharmOutcome.PlayerCharmDisallowed -> throw HexloveMishaps.disallowed("cupids_charm")
                else -> Unit
            }

            val charmResult = when (outcome) {
                CharmOutcome.CharmMob, CharmOutcome.CharmPlayer -> {
                    val cfg = CharmConfig.fromConfig()
                    val isPlayer = subject is net.minecraft.world.entity.player.Player
                    val maxHealth = (subject as? net.minecraft.world.entity.LivingEntity)?.maxHealth?.toDouble() ?: 1.0
                    CharmModel.resolve(power, CharmModel.resistance(maxHealth, isPlayer, cfg), isPlayer, cfg)
                }
                else -> null
            }

            SpellAction.Result(
                Spell(subject, master, power, outcome, charmResult),
                MediaCosts.cupidsCharm(outcome, power),
                listOf(
                    ParticleSpray.cloud(subject.position().add(0.0, subject.bbHeight / 2.0, 0.0), 1.0),
                    ParticleSpray.cloud(master.position().add(0.0, master.bbHeight / 2.0, 0.0), 1.0),
                ),
            )
        }

    private data class Spell(
        val subject: Entity,
        val master: Entity,
        val power: Double,
        val outcome: CharmOutcome,
        val charmResult: CharmResult?,
    ) : RenderedSpell {
        val durationSeconds: Double? = when (charmResult) {
            null -> null
            CharmResult.Permanent -> -1.0
            is CharmResult.Temporary -> charmResult.ticks.toDouble() / 20.0
        }

        override fun cast(env: CastingEnvironment) = ActionGuard.guardCast("cupids_charm") {
            when (outcome) {
                CharmOutcome.CharmMob, CharmOutcome.CharmPlayer -> charm(env)
                CharmOutcome.Breed -> court(env, CourtshipKind.SAME_SPECIES)
                CharmOutcome.Chimera -> court(env, CourtshipKind.CHIMERA)
                CharmOutcome.ZombieBreed -> court(env, CourtshipKind.ZOMBIE)
                CharmOutcome.ZombieVillagerBreed -> court(env, CourtshipKind.ZOMBIE_VILLAGER)
                else -> Unit
            }
        }

        private fun charm(env: CastingEnvironment) {
            val result = charmResult ?: return

            val expiresAt = when (result) {
                CharmResult.Permanent -> LoveBond.PERMANENT
                is CharmResult.Temporary -> env.world.gameTime + result.ticks
            }
            BondStore.setLove(subject, LoveBond(master.uuid, expiresAt))
            (master as? ServerPlayer)?.let {
                HexloveAdvancements.grant(it, HexloveAdvancements.CUPIDS_ARROW)
            }

            BreedingExecutor.hearts(env.world, subject)
            BreedingExecutor.hearts(env.world, master)
            // an arrow of pink light from the beloved to the one who just fell for them, ending in a
            // ring around them; from then on the charm is a faint glow they carry (see BondFx)
            BondFx.loveArrow(env.world, master, subject)

        }

        /**
         * Requirement 11, revised: no more children out of nowhere. The pair is betrothed and has to
         * find each other first; [CourtshipManager] decides what comes of it.
         */
        private fun court(env: CastingEnvironment, kind: CourtshipKind) {
            val offspring = when (kind) {
                CourtshipKind.SAME_SPECIES -> {
                    val cfg = BreedingConfig.fromConfig()
                    val bothReady = readyToMate(subject) && readyToMate(master)
                    BreedingPlanner.plan(bothReady, cfg) { min, max ->
                        if (max <= min) min else min + env.world.random.nextInt(max - min + 1)
                    }.offspring
                }
                // Requirements 12, 13.3: a blended child, or an undead one, always exactly one
                else -> 1
            }
            // Courtship is still an outcome of Cupid's Charm, so creature-to-creature casts must
            // get the same visible thread of magic as an ordinary love bond.
            BondFx.loveArrow(env.world, master, subject)
            CourtshipManager.begin(env.world, subject, master, kind, offspring)
        }

        private fun readyToMate(entity: Entity): Boolean {
            if (entity !is Animal) return false
            return BreedingSupport.isReadyToMate(BreedingSupport.facts(entity))
        }
    }
}
