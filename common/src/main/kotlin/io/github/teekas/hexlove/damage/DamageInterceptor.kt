package io.github.teekas.hexlove.damage

import io.github.teekas.hexlove.bond.BondHolder
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.marriage.MarriageManager
import io.github.teekas.hexlove.world.HexloveWorldData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.TamableAnimal
import java.util.Collections
import java.util.WeakHashMap

/** What to do with one blow. */
sealed interface DamageDecision {
    /** The blow never happens (Requirements 8.7, 9.3, 21.9). */
    data object Cancel : DamageDecision

    data object Pass : DamageDecision

    /** The blow lands and the jealous attacker feels a share of it (Requirement 16.8). */
    data class PassWithReflect(val fraction: Double) : DamageDecision
}

/** Snapshot of one blow, so the decision can be tested without a world. */
data class DamageFacts(
    val isDirectHit: Boolean,
    val attackerLovesVictim: Boolean,
    /** The victim is the active spouse of the attacker’s beloved. */
    val attackerLovesVictimSpouse: Boolean,
    /** A tame must never harm the active spouse of its owner. */
    val attackerIsPetOfVictimSpouse: Boolean,
    val spouses: Boolean,
    val attackerIsJealousOfVictim: Boolean,
    val attackerIsJealous: Boolean,
    /** Both are charmed onto the same master (Requirement 8, revised). */
    val shareTheSameBeloved: Boolean,
    val reflectFraction: Double,
)

object DamageRules {
    /**
     * Strictly directional: only a lover striking the object of their love is stopped. The reverse
     * direction is always allowed (Requirements 7.17, 9.12) — the victim can always fight back.
     *
     * Revised: two creatures charmed onto the same master also stop hitting each other. A harem
     * brawling with itself made charming a group useless. Jealousy is deliberately exempt: a jealous
     * creature strikes everybody who comes near the one it guards, harem-mates included.
     */
    fun decide(facts: DamageFacts): DamageDecision {
        if (!facts.isDirectHit) return DamageDecision.Pass
        if (facts.spouses) return DamageDecision.Cancel
        if (facts.attackerLovesVictim) return DamageDecision.Cancel
        if (facts.attackerIsPetOfVictimSpouse) return DamageDecision.Cancel
        if (facts.attackerLovesVictimSpouse && !facts.attackerIsJealous) return DamageDecision.Cancel
        if (facts.attackerIsJealousOfVictim && facts.reflectFraction > 0.0) {
            return DamageDecision.PassWithReflect(facts.reflectFraction)
        }
        if (facts.shareTheSameBeloved && !facts.attackerIsJealous) return DamageDecision.Cancel
        return DamageDecision.Pass
    }
}

object DamageInterceptor {
    /** Guards against the reflected damage re-entering this very handler. */
    private val reflecting: MutableSet<Entity> = Collections.newSetFromMap(WeakHashMap())
    /** A shared wound is re-applied deliberately below; neither half may recursively split it again. */
    private val sharing: MutableSet<Entity> = Collections.newSetFromMap(WeakHashMap())

    private const val MARRIAGE_DAMAGE_SHARE = 0.10f

    /** @return true if the blow must be cancelled. */
    fun onHurt(victim: LivingEntity, source: DamageSource, amount: Float): Boolean {
        val attacker = source.entity
        // A calm creature is never protected from harm. The first deliberate or indirect strike
        // breaks the spell before vanilla processes the damage, so its old hostility can return.
        if (attacker != null && BondStore.pheromone(victim) != null) {
            BondStore.clearPheromone(victim)
        }
        if (attacker === victim) return false
        if (attacker != null && attacker in reflecting) return false
        if (victim in sharing) return false

        val attackerHasBonds = attacker is BondHolder && attacker.hexloveHasBonds()
        val server = (victim.level() as? ServerLevel)?.server
        val spouses = server != null && attacker != null && MarriageManager.spouseOf(server, attacker.uuid) == victim.uuid
        val petOwner = (attacker as? TamableAnimal)?.ownerUUID
        val attackerIsPetOfVictimSpouse = server != null && petOwner != null &&
            MarriageManager.spouseOf(server, petOwner) == victim.uuid
        if (!attackerHasBonds && !spouses && !attackerIsPetOfVictimSpouse) {
            return shareMarriageDamage(victim, source, amount, server)
        }

        val attackerLove = attacker?.let(BondStore::love)?.target
        val jealousy = attacker?.let(BondStore::jealousy)
        val attackerIsJealous = jealousy != null
        val attackerLovesVictimSpouse = server != null && attackerLove != null &&
            MarriageManager.spouseOf(server, attackerLove) == victim.uuid
        val facts = DamageFacts(
            isDirectHit = DirectHit.isDirect(source, attacker),
            attackerLovesVictim = attackerLove == victim.uuid,
            attackerLovesVictimSpouse = attackerLovesVictimSpouse,
            attackerIsPetOfVictimSpouse = attackerIsPetOfVictimSpouse,
            spouses = spouses,
            attackerIsJealousOfVictim = jealousy?.target == victim.uuid,
            attackerIsJealous = attackerIsJealous,
            shareTheSameBeloved = attackerLove != null && attackerLove == BondStore.love(victim)?.target,
            reflectFraction = HexloveServerConfig.config.jealousy.damageReflectFraction,
        )

        val decision = DamageRules.decide(facts)

        // Requirement 16.6: a jealous creature that just made its point earns its moment of strength
        if (decision !is DamageDecision.Cancel && jealousy != null && attacker != null && jealousy.target != victim.uuid) {
            BondStore.noteJealousStrike(attacker, attacker.level().gameTime)
        }

        return when (decision) {
            DamageDecision.Cancel -> true
            DamageDecision.Pass -> shareMarriageDamage(victim, source, amount, server)
            is DamageDecision.PassWithReflect -> {
                attacker?.let { reflect(it, (amount * decision.fraction).toFloat()) }
                shareMarriageDamage(victim, source, amount, server)
            }
        }
    }

    /**
     * Ten percent is not erased: it becomes a real wound on the other spouse. If that spouse is
     * unavailable there is no free protection and the original damage is left completely alone.
     */
    private fun shareMarriageDamage(
        victim: LivingEntity,
        source: DamageSource,
        amount: Float,
        server: net.minecraft.server.MinecraftServer?,
    ): Boolean {
        val player = victim as? net.minecraft.server.level.ServerPlayer ?: return false
        val activeServer = server ?: return false
        val spouseId = MarriageManager.spouseOf(activeServer, player.uuid) ?: return false
        val spouse = activeServer.playerList.getPlayer(spouseId) ?: return false
        if (!spouse.isAlive || amount <= 0f) return false

        val shared = amount * MARRIAGE_DAMAGE_SHARE
        val retained = amount - shared
        if (shared <= 0f || retained <= 0f) return false
        sharing += victim
        sharing += spouse
        try {
            victim.hurt(source, retained)
            spouse.hurt(source, shared)
        } finally {
            sharing -= victim
            sharing -= spouse
        }
        return true
    }

    private fun reflect(attacker: Entity, amount: Float) {
        if (amount <= 0f || attacker !is LivingEntity) return
        reflecting.add(attacker)
        try {
            attacker.hurt(attacker.damageSources().magic(), amount)
        } finally {
            reflecting.remove(attacker)
        }
    }
}
