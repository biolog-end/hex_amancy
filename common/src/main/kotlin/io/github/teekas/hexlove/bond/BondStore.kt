package io.github.teekas.hexlove.bond

import io.github.teekas.hexlove.behavior.BehaviorController
import io.github.teekas.hexlove.behavior.BehaviorKind
import io.github.teekas.hexlove.behavior.BehaviorTracker
import io.github.teekas.hexlove.networking.BondSync
import io.github.teekas.hexlove.world.HaremMember
import io.github.teekas.hexlove.world.HexloveWorldData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.animal.Animal

/**
 * The single write point for bond state. Nobody touches [BondData] directly, which is what keeps the
 * invariants of Requirements 4.2, 4.3, 4.7, 3.11 and 5.1 true.
 *
 * Marriage records are never touched here (Requirement 4.8) — they live in the world data.
 */
object BondStore {
    fun love(entity: Entity): LoveBond? = data(entity)?.love
    fun jealousy(entity: Entity): JealousyBond? = data(entity)?.jealousy
    fun heartbreak(entity: Entity): HeartbreakState? = data(entity)?.heartbreak
    fun siren(entity: Entity): SirenState? = data(entity)?.siren
    fun pheromone(entity: Entity): PheromoneState? = data(entity)?.pheromone
    fun phantomIdeal(entity: Entity): PhantomIdealState? = data(entity)?.phantomIdeal
    fun courtship(entity: Entity): CourtshipState? = data(entity)?.courtship

    fun isHeartbroken(entity: Entity): Boolean = data(entity)?.heartbreak != null

    /** The two area spells only touch creatures whose own heart is currently uninvolved. */
    fun isUnattached(entity: Entity): Boolean {
        val data = data(entity) ?: return true
        return data.love == null && data.jealousy == null && data.heartbreak == null
    }

    private fun data(entity: Entity): BondData? =
        (entity as? BondHolder)?.let { if (it.hexloveHasBonds()) it.hexloveBonds() else null }

    /** Requirement 4.3: a new Love_Bond replaces the previous one. Requirement 18.4: not on a broken heart. */
    fun setLove(subject: Entity, bond: LoveBond) {
        if (isHeartbroken(subject)) return
        val data = subject.bonds
        data.love?.let { old -> if (old.target != bond.target) haremRemove(subject, old.target) }
        data.love = bond
        haremAdd(subject, bond)
        BehaviorTracker.track(subject)
        (subject as? Mob)?.let {
            BehaviorController.applyLove(it, bond)
            // Requirement 8.6, revised: a bow already drawn keeps firing unless the target is dropped
            BehaviorController.forgetForbiddenTarget(it)
        }
        markSync(subject)
    }

    fun clearLove(subject: Entity, reason: ClearReason) {
        val data = data(subject) ?: return
        val old = data.love ?: return
        data.love = null
        haremRemove(subject, old.target)
        (subject as? Mob)?.let { BehaviorController.remove(it, BehaviorKind.LOVE) }
        // BondSync.sync already ends with syncTarget, so the beloved's position is dropped here too.
        markSync(subject)
    }

    fun setJealousy(entity: Entity, bond: JealousyBond) {
        if (isHeartbroken(entity)) return
        val data = entity.bonds
        data.jealousy = bond
        BehaviorTracker.track(entity)
        (entity as? Mob)?.let { BehaviorController.applyJealousy(it, bond) }
        markSync(entity)
    }

    /** Requirement 16.6: remembers that the jealous one just landed a blow. */
    fun noteJealousStrike(entity: Entity, tick: Long) {
        val data = data(entity) ?: return
        val bond = data.jealousy ?: return
        data.jealousy = bond.copy(lastAttackTick = tick)
    }

    fun clearJealousy(entity: Entity) {
        val data = data(entity) ?: return
        if (data.jealousy == null) return
        data.jealousy = null
        (entity as? Mob)?.let { BehaviorController.remove(it, BehaviorKind.JEALOUSY) }
        markSync(entity)
    }

    fun setSiren(entity: Entity, state: SirenState) {
        if (isHeartbroken(entity)) return
        val data = entity.bonds
        data.siren = state
        BehaviorTracker.track(entity)
        (entity as? Mob)?.let { BehaviorController.applySiren(it, state) }
        markSync(entity)
    }

    fun clearSiren(entity: Entity) {
        val data = data(entity) ?: return
        if (data.siren == null) return
        data.siren = null
        (entity as? Mob)?.let { BehaviorController.remove(it, BehaviorKind.SIREN) }
        markSync(entity)
    }

    fun setPheromone(entity: Entity, state: PheromoneState) {
        if (!isUnattached(entity)) return
        if (data(entity)?.pheromone == state) return
        entity.bonds.pheromone = state
        BehaviorTracker.track(entity)
        (entity as? Mob)?.let(BehaviorController::applyPheromone)
        markSync(entity)
    }

    fun clearPheromone(entity: Entity) {
        val data = data(entity) ?: return
        if (data.pheromone == null) return
        data.pheromone = null
        (entity as? Mob)?.let { BehaviorController.remove(it, BehaviorKind.PHEROMONE) }
        markSync(entity)
    }

    fun setPhantomIdeal(entity: Entity, state: PhantomIdealState) {
        if (!isUnattached(entity)) return
        if (data(entity)?.phantomIdeal == state) return
        entity.bonds.phantomIdeal = state
        BehaviorTracker.track(entity)
        (entity as? Mob)?.let { BehaviorController.applyPhantomIdeal(it, state) }
        markSync(entity)
    }

    fun clearPhantomIdeal(entity: Entity) {
        val data = data(entity) ?: return
        if (data.phantomIdeal == null) return
        data.phantomIdeal = null
        (entity as? Mob)?.let { BehaviorController.remove(it, BehaviorKind.PHANTOM_IDEAL) }
        markSync(entity)
    }

    fun setCourtship(entity: Entity, state: CourtshipState) {
        if (isHeartbroken(entity)) return
        entity.bonds.courtship = state
        BehaviorTracker.track(entity)
        (entity as? Mob)?.let { BehaviorController.applyCourtship(it, state) }
    }

    /** Only the counter changes, so the behavior is left alone. */
    fun updateCourtship(entity: Entity, state: CourtshipState) {
        val data = data(entity) ?: return
        if (data.courtship == null) return
        data.courtship = state
    }

    fun clearCourtship(entity: Entity) {
        val data = data(entity) ?: return
        if (data.courtship == null) return
        data.courtship = null
        (entity as? Mob)?.let { BehaviorController.remove(it, BehaviorKind.COURTSHIP) }
    }

    /** Requirements 4.7, 18.3: heartbreak wipes love, jealousy and any betrothal in one operation. */
    fun setHeartbreak(entity: Entity, state: HeartbreakState) {
        batched(entity) {
            clearLove(entity, ClearReason.HEARTBREAK)
            clearJealousy(entity)
            clearSiren(entity)
            clearPheromone(entity)
            clearPhantomIdeal(entity)
            clearCourtship(entity)
            // and the plain vanilla mood goes with it
            (entity as? Animal)?.resetLove()
            entity.bonds.heartbreak = state
            BehaviorTracker.track(entity)
        }
    }

    fun clearHeartbreak(entity: Entity) {
        val data = data(entity) ?: return
        if (data.heartbreak == null) return
        data.heartbreak = null
        markSync(entity)
    }

    fun clearAll(entity: Entity, reason: ClearReason) {
        batched(entity) {
            clearLove(entity, reason)
            clearJealousy(entity)
            clearSiren(entity)
            clearPheromone(entity)
            clearPhantomIdeal(entity)
            clearCourtship(entity)
            clearHeartbreak(entity)
            (entity as? Mob)?.let { BehaviorController.removeAll(it) }
            BehaviorTracker.untrack(entity)
        }
    }

    /**
     * Requirement 3.3: reinstall behavior from persisted data when the entity loads.
     *
     * This is idempotent on purpose: on Fabric, `EntityEvent.ADD` never fires for entities that come
     * back from chunk storage, so [io.github.teekas.hexlove.behavior.BondTicker] also sweeps for
     * bonded entities that nobody told us about and calls this again.
     */
    fun restoreBehavior(entity: Entity) {
        val data = data(entity) ?: return
        if (data.isEmpty) return
        BehaviorTracker.track(entity)
        val mob = entity as? Mob ?: return
        data.love?.let {
            BehaviorController.applyLove(mob, it)
            BehaviorController.forgetForbiddenTarget(mob)
        }
        data.jealousy?.let { BehaviorController.applyJealousy(mob, it) }
        data.siren?.let { BehaviorController.applySiren(mob, it) }
        data.pheromone?.let { BehaviorController.applyPheromone(mob) }
        data.phantomIdeal?.let { BehaviorController.applyPhantomIdeal(mob, it) }
        data.courtship?.let { BehaviorController.applyCourtship(mob, it) }
    }

    /** Keeps a conservative health snapshot for Tithe while a beloved creature is loaded. */
    fun refreshHaremMember(entity: Entity) {
        val love = love(entity) ?: return
        haremAdd(entity, love)
    }

    private fun haremAdd(subject: Entity, love: LoveBond) {
        val server = subject.level().server ?: return
        val health = (subject as? LivingEntity)?.health ?: 0f
        HexloveWorldData.get(server)
            .addHaremMember(
                love.target,
                HaremMember(subject.uuid, subject.level().dimension().location(), health, love.expiresAt),
            )
    }

    private fun haremRemove(subject: Entity, owner: java.util.UUID) {
        val server = subject.level().server ?: return
        HexloveWorldData.get(server).removeHaremMember(owner, subject.uuid)
    }

    /**
     * Wiping every state used to send the client one full snapshot per field, six of which described
     * a half-cleared entity nobody ever saw. Only the finished state is worth a packet — and each of
     * those packets asked the marriage record whether both rings were still carried.
     *
     * A depth counter rather than a boolean: nothing nests today, but a plain flag would silently
     * swallow the outer batch's packet the day something does. Cheap insurance, no behaviour change.
     */
    private var suppressedSyncs = 0

    private inline fun batched(entity: Entity, body: () -> Unit) {
        suppressedSyncs++
        try {
            body()
        } finally {
            suppressedSyncs--
        }
        markSync(entity)
    }

    private fun markSync(entity: Entity) {
        if (suppressedSyncs > 0) return
        (entity as? ServerPlayer)?.let(BondSync::sync)
    }
}
