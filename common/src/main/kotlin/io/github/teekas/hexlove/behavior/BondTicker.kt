package io.github.teekas.hexlove.behavior

import io.github.teekas.hexlove.bond.BondData
import io.github.teekas.hexlove.bond.BondGate
import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.bond.ClearReason
import io.github.teekas.hexlove.bond.bonds
import io.github.teekas.hexlove.bond.hasBonds
import io.github.teekas.hexlove.charm.CourtshipManager
import io.github.teekas.hexlove.compat.CarriedEntityAccess
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.fx.BondFx
import io.github.teekas.hexlove.networking.BondSync
import io.github.teekas.hexlove.sameLevelLiving
import io.github.teekas.hexlove.world.DebtLedger
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.max

/** Which states of one entity have run out at [now]. Pure; a permanent bond never expires. */
enum class ExpiredState { LOVE, JEALOUSY, HEARTBREAK, SIREN, PHEROMONE, PHANTOM_IDEAL }

object BondExpiry {
    fun expired(data: BondData, now: Long): Set<ExpiredState> {
        val out = mutableSetOf<ExpiredState>()
        data.love?.let { if (!it.permanent && it.expiresAt != Long.MAX_VALUE && now >= it.expiresAt) out += ExpiredState.LOVE }
        data.jealousy?.let { if (now >= it.expiresAt) out += ExpiredState.JEALOUSY }
        data.heartbreak?.let { if (now >= it.expiresAt) out += ExpiredState.HEARTBREAK }
        data.siren?.let { if (now >= it.expiresAt) out += ExpiredState.SIREN }
        data.pheromone?.let { if (now >= it.expiresAt) out += ExpiredState.PHEROMONE }
        data.phantomIdeal?.let { if (now >= it.expiresAt) out += ExpiredState.PHANTOM_IDEAL }
        return out
    }
}

/**
 * Requirements 9.6, 9.7, 9.11, 4.6: the pull only starts past the threshold, and dies with the target.
 *
 * Revised after playtesting: the first version snapped to full strength the instant the threshold was
 * crossed, which felt like walking into a wall. Now it eases in over [ramp] blocks, so leaving is
 * a growing reluctance rather than a barrier.
 */
object LovePull {
    fun pullVector(self: Vec3, target: Vec3?, threshold: Double, ramp: Double, strength: Double): Vec3 {
        if (target == null) return Vec3.ZERO
        val delta = target.subtract(self)
        val distance = delta.length()
        if (distance <= threshold || distance < 1.0e-4) return Vec3.ZERO
        val progress = ((distance - threshold) / max(ramp, 1.0e-4)).coerceIn(0.0, 1.0)
        // quadratic ease-in: barely noticeable at first, firm only far away
        val eased = progress * progress
        if (eased <= 0.0) return Vec3.ZERO
        return delta.scale(strength * eased / distance)
    }
}

/**
 * Requirement 16.5/16.6: the ultimatum sets are mutually exclusive — either the jealous one is
 * fuming at a stranger who overstayed, or it is proud of a hit it just landed. Never both.
 */
enum class UltimatumEffect { NONE, WEAKNESS_AND_SLOWNESS, STRENGTH }

object JealousyUltimatum {
    fun effectFor(strangerNearby: Boolean, ticksSinceStranger: Int, ultimatumTicks: Int, struckRecently: Boolean): UltimatumEffect =
        when {
            struckRecently -> UltimatumEffect.STRENGTH
            strangerNearby && ticksSinceStranger >= ultimatumTicks -> UltimatumEffect.WEAKNESS_AND_SLOWNESS
            else -> UltimatumEffect.NONE
        }
}

/**
 * Walks only the entities that carry a state of this mod (Requirement 3.7): expiry, dead bond
 * targets, continuous effects. Runs every [INTERVAL] ticks for bookkeeping, every tick for the pull.
 *
 * It also *reconciles*: on Fabric, `EntityEvent.ADD` is only fired for entities that are freshly
 * spawned, never for ones that come back from chunk storage. Without the sweep below, every charmed
 * creature quietly forgot how to behave after a reload while its bond sat there untouched — which is
 * exactly what "all the love effects vanish when I rejoin" looked like from the outside. The same
 * sweep also settles Tithe debts for restored creatures, because the load event has that gap too.
 */
object BondTicker {
    private const val INTERVAL = 10
    private const val SYNC_INTERVAL = 5
    private const val RECONCILE_INTERVAL = 100
    private const val HAREM_SNAPSHOT_INTERVAL = 100

    private val strangerSince = HashMap<UUID, Int>()
    private val ultimatumApplied = HashSet<UUID>()
    private val strangerDistance = HashMap<UUID, Double>()

    fun tick(level: ServerLevel) {
        val now = level.gameTime

        // every tick: an arrow of light that only moved twice a second would not read as an arrow
        BondFx.tick(level)

        // every tick: the pull has to be smooth
        for (player in level.players()) {
            if (!player.hasBonds) continue
            applyPull(player)
            applySirenPull(player)
        }

        if (now % SYNC_INTERVAL == 0L) {
            for (player in level.players()) {
                if (!player.hasBonds) continue
                BondSync.syncTarget(player, strangerDistance[player.uuid])
            }
        }

        if (now % RECONCILE_INTERVAL == 0L) reconcile(level)

        if (now % INTERVAL != 0L) return

        for (entity in BehaviorTracker.snapshot(level)) {
            if (!entity.isAlive) {
                BehaviorTracker.untrack(entity)
                continue
            }
            if (!entity.hasBonds) {
                BehaviorTracker.untrack(entity)
                continue
            }
            tickEntity(level, entity, now)
        }
    }

    /** Reinstalls behavior for bonded entities nobody announced to us. Cheap: the filter matches almost nothing. */
    private fun reconcile(level: ServerLevel) {
        DebtLedger.settleLoaded(level)
        val strays = level.getEntities(EntityTypeTest.forClass(Entity::class.java)) {
            it.hasBonds && !BehaviorTracker.isTracked(it)
        }
        for (entity in strays) BondStore.restoreBehavior(entity)
    }

    private fun tickEntity(level: ServerLevel, entity: Entity, now: Long) {
        val data = entity.bonds
        data.love?.let {
            if (!it.permanent && it.expiresAt != Long.MAX_VALUE && now >= it.expiresAt) {
                BondStore.clearLove(entity, ClearReason.EXPIRED)
            }
        }
        data.jealousy?.let {
            if (now >= it.expiresAt) {
                BondStore.clearJealousy(entity)
                clearUltimatum(entity)
            }
        }
        data.heartbreak?.let { if (now >= it.expiresAt) BondStore.clearHeartbreak(entity) }
        data.siren?.let { if (now >= it.expiresAt) BondStore.clearSiren(entity) }
        data.pheromone?.let { if (now >= it.expiresAt) BondStore.clearPheromone(entity) }
        data.phantomIdeal?.let { if (now >= it.expiresAt) BondStore.clearPhantomIdeal(entity) }

        // Requirement 3.8: a bond whose object is gone for good is a bond to nobody
        data.love?.let { love ->
            val target = entity.sameLevelLiving(love.target)
            if (target != null && !target.isAlive) BondStore.clearLove(entity, ClearReason.TARGET_GONE)
        }
        data.jealousy?.let { jealousy ->
            val target = entity.sameLevelLiving(jealousy.target)
            if (target != null && !target.isAlive) BondStore.clearJealousy(entity)
        }
        data.phantomIdeal?.let { ideal ->
            val target = entity.sameLevelLiving(ideal.ideal)
            if (target == null || !target.isAlive) BondStore.clearPhantomIdeal(entity)
        }

        // Requirement 8.6, revised: a skeleton with an old grudge keeps firing until the target is dropped
        if (data.love != null || data.pheromone != null || data.phantomIdeal != null) {
            (entity as? Mob)?.let(BehaviorController::forgetForbiddenTarget)
        }

        // An unloaded tithe source has no entity object to inspect. Record the last truthful health
        // occasionally while it is here, without writing world data every single tick.
        if (data.love != null && now % HAREM_SNAPSHOT_INTERVAL == 0L) BondStore.refreshHaremMember(entity)

        // Requirement 18, revised: a broken heart is visible, the way every other effect is
        data.heartbreak?.let { heartbreakParticles(level, entity) }

        bondParticles(level, entity, data, now)

        if (data.courtship != null) CourtshipManager.tick(level, entity, now, INTERVAL)

        if (entity is ServerPlayer) tickJealousPlayer(entity, now)
    }

    /**
     * What an ongoing bond looks like from the outside. Deliberately sparse: a charm can be
     * permanent, so a charmed creature is allowed one mote a second and no more. Jealousy runs out
     * on its own and is meant to be alarming, so it gets three every half second, and the song
     * re-announces itself with a whole beam — but only every three seconds, and only while the one
     * being called is still far enough away for the call to mean anything.
     */
    private fun bondParticles(level: ServerLevel, entity: Entity, data: BondData, now: Long) {
        data.love?.let { love ->
            if (now % BondFx.LOVE_AMBIENT_INTERVAL != 0L) return@let
            val heldByLover = (entity as? ServerPlayer)?.let {
                CarriedEntityAccess.isCarriedBy(it, love.target)
            } == true
            if (heldByLover) BondFx.carriedLoveAmbient(level, entity) else BondFx.loveAmbient(level, entity)
        }
        if (data.jealousy != null && now % BondFx.JEALOUSY_AMBIENT_INTERVAL == 0L) {
            BondFx.jealousyAmbient(level, entity)
        }
        data.siren?.let { siren ->
            if (now % BondFx.SIREN_CALL_INTERVAL != 0L) return@let
            val caller = entity.sameLevelLiving(siren.caller) ?: return@let
            if (caller.distanceTo(entity) <= BondFx.SIREN_QUIET_DISTANCE) return@let
            BondFx.sirenCall(level, caller, entity)
        }
        if (data.pheromone != null) BondFx.pheromoneAmbient(level, entity)
    }

    private fun heartbreakParticles(level: ServerLevel, entity: Entity) {
        level.sendParticles(
            ParticleTypes.SMOKE,
            entity.x, entity.y + entity.bbHeight * 0.75, entity.z,
            3, 0.25, 0.2, 0.25, 0.01,
        )
    }

    private fun applyPull(player: ServerPlayer) {
        val love = BondStore.love(player) ?: return
        val cfg = HexloveServerConfig.config.love
        // Carry On removes a picked-up entity from the level. Its NBT is still in this player's
        // carry data, so the beloved is effectively right here: no pull, strongest love overlay.
        val targetPosition = player.sameLevelLiving(love.target)?.position()
            ?: player.takeIf { CarriedEntityAccess.isCarriedBy(it, love.target) }?.position()
        val pull = LovePull.pullVector(
            player.position(),
            targetPosition,
            cfg.pullDistance,
            cfg.pullRampDistance,
            cfg.pullStrength,
        )
        if (pull == Vec3.ZERO) return
        // the player keeps control: this is a nudge added to their own movement, not teleportation
        player.deltaMovement = player.deltaMovement.add(pull)
        player.hurtMarked = true
    }

    /** Requirement 20, revised: the song leads players too, not only mobs with a pathfinder. */
    private fun applySirenPull(player: ServerPlayer) {
        val siren = BondStore.siren(player) ?: return
        val cfg = HexloveServerConfig.config.siren
        val caller = player.sameLevelLiving(siren.caller) ?: return
        val pull = LovePull.pullVector(
            player.position(),
            caller.position(),
            cfg.playerPullDistance,
            cfg.playerPullDistance,
            cfg.playerPullStrength,
        )
        if (pull == Vec3.ZERO) return
        player.deltaMovement = player.deltaMovement.add(pull)
        player.hurtMarked = true
    }

    private fun tickJealousPlayer(player: ServerPlayer, now: Long) {
        val jealousy = BondStore.jealousy(player)
        if (jealousy == null) {
            forgetJealousy(player)
            return
        }
        val cfg = HexloveServerConfig.config.jealousy
        // A held protected entity is guarded from the carrier's position. This keeps player
        // jealousy active around a carried beloved instead of silently suspending it.
        val guarded = player.sameLevelLiving(jealousy.target)
            ?: player.takeIf { CarriedEntityAccess.isCarriedBy(it, jealousy.target) }
        if (guarded == null) {
            strangerDistance.remove(player.uuid)
            return
        }

        val nearest = player.level()
            .getEntitiesOfClass(LivingEntity::class.java, guarded.boundingBox.inflate(cfg.guardRadius)) {
                it !== guarded && it !== player && it.isAlive && !BondGate.forbidsTargeting(player, it)
            }
            .minByOrNull { it.distanceToSqr(guarded) }

        if (nearest != null) {
            strangerDistance[player.uuid] = Math.sqrt(nearest.distanceToSqr(guarded))
        } else {
            strangerDistance.remove(player.uuid)
        }

        val elapsed = if (nearest != null) (strangerSince[player.uuid] ?: 0) + INTERVAL else 0
        strangerSince[player.uuid] = elapsed

        val struckRecently = jealousy.lastAttackTick > 0 && now - jealousy.lastAttackTick <= cfg.ultimatumTicks
        when (JealousyUltimatum.effectFor(nearest != null, elapsed, cfg.ultimatumTicks, struckRecently)) {
            UltimatumEffect.STRENGTH -> {
                ultimatumApplied.add(player.uuid)
                player.removeEffect(MobEffects.WEAKNESS)
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
                player.addEffect(MobEffectInstance(MobEffects.DAMAGE_BOOST, INTERVAL * 3, 0, true, false))
            }

            UltimatumEffect.WEAKNESS_AND_SLOWNESS -> {
                ultimatumApplied.add(player.uuid)
                player.removeEffect(MobEffects.DAMAGE_BOOST)
                player.addEffect(MobEffectInstance(MobEffects.WEAKNESS, INTERVAL * 3, 0, true, false))
                player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, INTERVAL * 3, 0, true, false))
            }

            // Requirement 16.9, revised: the ultimatum has to *end*. Letting the short instances lapse
            // on their own left the debuff hanging around long past the reason for it.
            UltimatumEffect.NONE -> clearUltimatum(player)
        }
    }

    /** Requirement 16.9: our own effects go away with the bond — and only ours. */
    private fun clearUltimatum(entity: Entity) {
        if (entity !is LivingEntity) return
        if (ultimatumApplied.remove(entity.uuid)) {
            entity.removeEffect(MobEffects.WEAKNESS)
            entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
            entity.removeEffect(MobEffects.DAMAGE_BOOST)
        }
        strangerSince.remove(entity.uuid)
    }

    private fun forgetJealousy(player: ServerPlayer) {
        clearUltimatum(player)
        strangerDistance.remove(player.uuid)
    }
}
