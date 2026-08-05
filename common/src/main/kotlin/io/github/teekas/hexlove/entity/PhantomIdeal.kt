package io.github.teekas.hexlove.entity

import io.github.teekas.hexlove.bond.BondStore
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.bond.PhantomIdealState
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.fx.BondFx
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * A player-shaped, intangible picture of the caster's desire. It is a real entity only so clients
 * can render and track it normally; it cannot be hit, pushed, or select a target of its own.
 */
class PhantomIdeal(type: EntityType<out PhantomIdeal>, level: Level) : PathfinderMob(type, level) {
    private var owner: UUID? = null
    private var expiresAt: Long = 0L

    init {
        noPhysics = true
        isNoGravity = true
        if (!level.isClientSide) {
            setStatueFacing(level.random.nextFloat() * 360f, 0f)
        }
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(STATUE_YAW, 0f)
        entityData.define(STATUE_PITCH, 0f)
    }

    override fun registerGoals() = Unit

    override fun isPushable(): Boolean = false

    override fun canBeCollidedWith(): Boolean = false

    /**
     * The apparition ignores gameplay damage, but administrative removal must always work.
     * LivingEntity implements /kill through the generic-kill damage source, so rejecting every
     * source here made command-spawned ideals impossible to clean up.
     */
    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (source.`is`(DamageTypes.GENERIC_KILL)) {
            discard()
            return true
        }
        return false
    }

    override fun removeWhenFarAway(distanceToClosestPlayer: Double): Boolean = false

    fun manifest(caster: LivingEntity, position: Vec3, until: Long) {
        owner = caster.uuid
        expiresAt = until
        // Borrow the caster's current direction rather than staring back at them. This gives every
        // statue a deliberate initial pose without making it track the caster after manifestation.
        val yaw = caster.yRot
        setStatueFacing(yaw, 0f)
        moveTo(position.x, position.y, position.z, yaw, 0f)
        lockStatuePose()
    }

    private fun setStatueFacing(yaw: Float, pitch: Float) {
        entityData.set(STATUE_YAW, yaw)
        entityData.set(STATUE_PITCH, pitch)
    }

    /** Mob controls are allowed to tick, then all rotation and motion is restored to this pose. */
    private fun lockStatuePose() {
        val yaw = entityData.get(STATUE_YAW)
        val pitch = entityData.get(STATUE_PITCH)
        yRot = yaw
        yRotO = yaw
        yHeadRot = yaw
        yHeadRotO = yaw
        yBodyRot = yaw
        yBodyRotO = yaw
        xRot = pitch
        xRotO = pitch
    }

    /** Includes creatures walking into the field later, not just the ones present on the cast tick. */
    fun attractNearby() {
        val serverLevel = level() as? ServerLevel ?: return
        val cfg = HexloveServerConfig.config.phantomIdeal
        val radius = cfg.lureRadius
        // A command-spawned ideal has no spell timer. Giving nearby mobs expiry 0 made the ticker
        // clear and reinstall their lure every ten ticks, which looked like movement in bursts.
        val state = PhantomIdealState(uuid, if (expiresAt > 0L) expiresAt else Long.MAX_VALUE)
        val area = AABB.ofSize(position(), radius * 2.0, radius * 2.0, radius * 2.0)
        val mobs = serverLevel.getEntitiesOfClass(Mob::class.java, area) {
            it !== this && it.isAlive && it.distanceToSqr(this) <= radius * radius && BondStore.isUnattached(it)
        }
        for (mob in mobs) BondStore.setPhantomIdeal(mob, state)
        if (mobs.size >= 5) {
            owner?.let(serverLevel.server.playerList::getPlayer)?.let {
                HexloveAdvancements.grant(it, HexloveAdvancements.FLAWLESS_ILLUSION)
            }
        }
    }

    override fun tick() {
        super.tick()
        noPhysics = true
        isNoGravity = true
        deltaMovement = Vec3.ZERO
        lockStatuePose()
        val serverLevel = level() as? ServerLevel ?: return
        if (expiresAt > 0L && serverLevel.gameTime >= expiresAt) {
            discard()
            return
        }
        if (tickCount % ATTRACT_INTERVAL == 0) attractNearby()
        if (tickCount % AMBIENT_INTERVAL == 0) BondFx.phantomAmbient(serverLevel, this)
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        owner?.let { tag.putUUID(OWNER_TAG, it) }
        tag.putLong(EXPIRES_AT_TAG, expiresAt)
        tag.putFloat(STATUE_YAW_TAG, entityData.get(STATUE_YAW))
        tag.putFloat(STATUE_PITCH_TAG, entityData.get(STATUE_PITCH))
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        owner = if (tag.hasUUID(OWNER_TAG)) tag.getUUID(OWNER_TAG) else null
        expiresAt = tag.getLong(EXPIRES_AT_TAG)
        when {
            tag.contains(STATUE_YAW_TAG) -> setStatueFacing(
                tag.getFloat(STATUE_YAW_TAG),
                tag.getFloat(STATUE_PITCH_TAG),
            )
            tag.contains("Rotation", Tag.TAG_LIST.toInt()) -> setStatueFacing(yRot, xRot)
        }
        lockStatuePose()
    }

    companion object {
        private const val OWNER_TAG = "hexlove:phantom_owner"
        private const val EXPIRES_AT_TAG = "hexlove:phantom_expires_at"
        private const val STATUE_YAW_TAG = "hexlove:phantom_statue_yaw"
        private const val STATUE_PITCH_TAG = "hexlove:phantom_statue_pitch"
        private const val ATTRACT_INTERVAL = 10
        private const val AMBIENT_INTERVAL = 10
        private val STATUE_YAW: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(PhantomIdeal::class.java, EntityDataSerializers.FLOAT)
        private val STATUE_PITCH: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(PhantomIdeal::class.java, EntityDataSerializers.FLOAT)

        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 1.0)
            .add(Attributes.MOVEMENT_SPEED, 0.0)
    }
}
