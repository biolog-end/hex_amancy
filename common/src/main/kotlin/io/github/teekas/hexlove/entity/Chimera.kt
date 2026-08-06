package io.github.teekas.hexlove.entity

import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.registry.HexloveEntities
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.registry.HexloveSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.control.MoveControl
import net.minecraft.world.entity.ai.goal.BreedGoal
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.FollowParentGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.PanicGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.TemptGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.Cow
import net.minecraft.world.entity.animal.Pig
import net.minecraft.world.entity.animal.Sheep
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import java.util.UUID
import kotlin.math.min

/**
 * Requirement 12: the child of two animals of different species.
 *
 * Its vital statistics are written by [io.github.teekas.hexlove.charm.ChimeraFactory] from the average
 * of its parents, so the numbers here are only the fallback for a Chimera that was spawned some other
 * way — by a command, or by two Chimeras of its own.
 *
 * A Chimera is a slow, awkward predator: adults hunt common farm animals and may stalk players,
 * while young Chimeras only hunt young farm animals.
 */
class Chimera(type: EntityType<out Chimera>, level: Level) : Animal(type, level) {
    private var playerTruceId: UUID? = null
    private var playerTruceTicks: Int = 0
    private var clientHeadbuttTicks: Int = 0
    private var clientHeadbuttTicksOld: Int = 0

    init {
        moveControl = SlowTurningMoveControl(this)
    }

    /** Synced because collision and the client renderer must agree about its physical scale. */
    var sizeFactor: Float
        get() = entityData.get(SIZE_FACTOR)
        private set(value) = entityData.set(SIZE_FACTOR, value.coerceAtLeast(MIN_SIZE_FACTOR))

    /** Kept for the promised persistent lineage independently of the shared authored appearance. */
    var firstParentType: ResourceLocation? = null
        private set
    var secondParentType: ResourceLocation? = null
        private set

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, PanicGoal(this, 1.4))
        goalSelector.addGoal(2, MeleeAttackGoal(this, 1.0, false))
        goalSelector.addGoal(3, BreedGoal(this, 1.0))
        goalSelector.addGoal(4, TemptGoal(this, 1.1, RAW_ANIMAL_MEAT_INGREDIENT, false))
        goalSelector.addGoal(5, FollowParentGoal(this, 1.1))
        goalSelector.addGoal(6, WaterAvoidingRandomStrollGoal(this, 1.0))
        goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 6.0f))
        goalSelector.addGoal(8, RandomLookAroundGoal(this))

        targetSelector.addGoal(
            1,
            NearestAttackableTargetGoal(
                this,
                Animal::class.java,
                10,
                true,
                false,
                ::canHuntAnimal,
            ),
        )
        targetSelector.addGoal(
            2,
            NearestAttackableTargetGoal(
                this,
                Player::class.java,
                PLAYER_TARGET_SCAN_INTERVAL,
                true,
                false,
                ::canHuntPlayer,
            ),
        )
    }

    private fun canHuntAnimal(candidate: LivingEntity): Boolean {
        val animal = candidate as? Animal ?: return false
        if (animal === this || !animal.isAlive) return false
        val isFarmAnimal = animal is Pig || animal is Cow || animal is Sheep
        return isFarmAnimal && (!isBaby || animal.isBaby)
    }

    private fun canHuntPlayer(candidate: LivingEntity): Boolean {
        val player = candidate as? Player ?: return false
        return !isBaby && (playerTruceTicks <= 0 || player.uuid != playerTruceId)
    }

    override fun setTarget(target: LivingEntity?) {
        if (target is Player && playerTruceTicks > 0 && target.uuid == playerTruceId) {
            super.setTarget(null)
            return
        }
        super.setTarget(target)
    }

    /**
     * The generic melee swing is too short and is not a dependable animation clock for this custom
     * model. A successful hit therefore emits a transient vanilla entity event. It carries no saved
     * state and does not alter damage or the melee goal's cooldown.
     */
    override fun doHurtTarget(target: Entity): Boolean {
        val hit = super.doHurtTarget(target)
        if (hit && !level().isClientSide) {
            level().broadcastEntityEvent(this, HEADBUTT_EVENT)
            playSound(HexloveSounds.CHIMERA_HEADBUTT.value, soundVolume, voicePitch)
        }
        return hit
    }

    override fun handleEntityEvent(event: Byte) {
        if (event == HEADBUTT_EVENT) {
            clientHeadbuttTicksOld = HEADBUTT_ANIMATION_TICKS
            clientHeadbuttTicks = HEADBUTT_ANIMATION_TICKS - 1
        } else {
            super.handleEntityEvent(event)
        }
    }

    /** Smooth client-only progress in the inclusive 0..1 range; zero means no active headbutt. */
    fun headbuttAnimationProgress(partialTick: Float): Float {
        if (clientHeadbuttTicks <= 0 && clientHeadbuttTicksOld <= 0) return 0.0f
        val remaining = Mth.lerp(
            partialTick.coerceIn(0.0f, 1.0f),
            clientHeadbuttTicksOld.toFloat(),
            clientHeadbuttTicks.toFloat(),
        )
        return (1.0f - remaining / HEADBUTT_ANIMATION_TICKS.toFloat()).coerceIn(0.0f, 1.0f)
    }

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        val wasHurt = super.hurt(source, amount)
        val attacker = source.entity as? Player
        if (wasHurt && attacker != null && !level().isClientSide) {
            playerTruceId = attacker.uuid
            playerTruceTicks = PLAYER_TRUCE_TICKS
            if (target?.uuid == attacker.uuid) {
                target = null
                navigation.stop()
            }
            setLastHurtByMob(null)
        }
        return wasHurt
    }

    override fun aiStep() {
        super.aiStep()
        if (level().isClientSide) {
            clientHeadbuttTicksOld = clientHeadbuttTicks
            if (clientHeadbuttTicks > 0) clientHeadbuttTicks--
        } else if (playerTruceTicks > 0) {
            playerTruceTicks--
            if (playerTruceTicks == 0) playerTruceId = null
        }
    }

    override fun getHeadRotSpeed(): Int = 4

    override fun getMaxHeadYRot(): Int = 32

    override fun getAmbientSound(): SoundEvent = HexloveSounds.CHIMERA_AMBIENT.value

    override fun getHurtSound(source: DamageSource): SoundEvent = HexloveSounds.CHIMERA_HURT.value

    override fun getDeathSound(): SoundEvent = HexloveSounds.CHIMERA_DEATH.value

    /** Three feet carry the weight and the fourth is the wrong shape, so the gait reads as a lurch. */
    override fun playStepSound(pos: BlockPos, state: BlockState) {
        playSound(HexloveSounds.CHIMERA_STEP.value, 0.16f * soundVolume, voicePitch)
    }

    /** A chimera is only ever as large as its parents were, and it sounds it. */
    override fun getSoundVolume(): Float = (0.6f + 0.4f * sizeFactor).coerceIn(0.5f, 1.3f)

    override fun getVoicePitch(): Float =
        (super.getVoicePitch() * (1.18f - 0.18f * sizeFactor)).coerceIn(0.55f, 1.9f)

    /**
     * Two chimeras can breed, but only from raw meat of ordinary animals. Their child uses the same
     * parent initialisation as a charm-created chimera, so its size and health never fall back to
     * defaults.
     */
    override fun getBreedOffspring(level: ServerLevel, other: AgeableMob): AgeableMob? {
        val mate = other as? Chimera ?: return null
        val breeding = HexloveServerConfig.config.breeding
        return HexloveEntities.CHIMERA.value.create(level)?.apply {
            initializeFromParents(this@Chimera, mate, breeding.chimeraMaxSize, breeding.chimeraMaxHealth,
                sizeNoise = breeding.chimeraSizeNoise, healthNoise = breeding.chimeraHealthNoise,
                rng = level.random)
        }
    }

    override fun spawnChildFromBreeding(level: ServerLevel, mate: Animal) {
        super.spawnChildFromBreeding(level, mate)
        if (mate is Chimera) {
            HexloveAdvancements.grantNearby(level, position(), HexloveAdvancements.DOUBLE_MUTATION)
        }
    }

    override fun isFood(stack: ItemStack): Boolean = stack.item in RAW_ANIMAL_MEAT

    override fun dropCustomDeathLoot(source: DamageSource, looting: Int, recentlyHit: Boolean) {
        super.dropCustomDeathLoot(source, looting, recentlyHit)
        val meat = if (isOnFire) HexloveItems.COOKED_CHIMERA_MEAT.value else HexloveItems.CHIMERA_MEAT.value
        // Comparable to the 1–3 beef of a cow; Looting extends the upper end without ever making
        // this custom creature an unlimited food source.
        spawnAtLocation(ItemStack(meat, 1 + random.nextInt(3 + looting)))

        // This one pool has the same aggregate 0–2 (+Looting) chance as cow leather. Individual
        // rolls choose a single material, so leather, wool, feathers and bone meal divide that
        // chance instead of all appearing together on every kill.
        repeat(random.nextInt(3 + looting)) {
            spawnAtLocation(ItemStack(randomBonusDrop()))
        }
    }

    private fun randomBonusDrop(): Item = when (random.nextInt(4)) {
        0 -> Items.LEATHER
        1 -> Items.FEATHER
        2 -> Items.BONE_MEAL
        else -> randomNaturalWool()
    }

    /** The colour weights apply after wool has been selected from the shared bonus pool. */
    private fun randomNaturalWool(): Item = when (random.nextInt(100)) {
        in 0..79 -> Items.WHITE_WOOL
        in 80..89 -> Items.GRAY_WOOL
        in 90..94 -> Items.BROWN_WOOL
        else -> Items.BLACK_WOOL
    }

    /**
     * The average of the parents is only the *centre* of the child's distribution. A small
     * two-tailed scatter is applied on top, so most chimeras land near the mean but rare
     * births come out either as an ant-sized runt or as an oversized horror. Health scatter is
     * independent of size, so a huge chimera can still be surprisingly fragile (a farm-friendly
     * jackpot) and vice-versa. Pass [sizeNoise] / [healthNoise] = 0.0 for the old strict-average
     * behaviour. [rng] must be non-null whenever noise > 0.
     */
    fun initializeFromParents(
        first: Animal,
        second: Animal,
        maxSize: Double,
        maxHealth: Double,
        sizeNoise: Double = 0.0,
        healthNoise: Double = 0.0,
        rng: RandomSource? = null,
    ) {
        firstParentType = BuiltInRegistries.ENTITY_TYPE.getKey(first.type)
        secondParentType = BuiltInRegistries.ENTITY_TYPE.getKey(second.type)
        val averageParentSize = (first.bbWidth + first.bbHeight + second.bbWidth + second.bbHeight) / 4.0
        val baseSize = averageParentSize / BASE_BODY_SIZE
        val scatteredSize = scatter(baseSize, sizeNoise, rng)
        sizeFactor = scatteredSize.toFloat().coerceIn(MIN_SIZE_FACTOR, maxSize.toFloat())
        val firstHealth = first.getAttribute(Attributes.MAX_HEALTH)?.baseValue ?: first.maxHealth.toDouble()
        val secondHealth = second.getAttribute(Attributes.MAX_HEALTH)?.baseValue ?: second.maxHealth.toDouble()
        val baseHealth = (firstHealth + secondHealth) / 2.0
        val scatteredHealth = scatter(baseHealth, healthNoise, rng)
        getAttribute(Attributes.MAX_HEALTH)?.let { health ->
            health.baseValue = scatteredHealth.coerceIn(0.001, maxHealth.coerceAtLeast(0.001))
        }
        health = this.maxHealth
        refreshDimensions()
    }

    /**
     * Multiplicative Gaussian-ish scatter with unit standard deviation, cheap enough to run in a
     * mob spawn hot path. The sum of four uniforms is centred and normalised so that [noise]
     * reads as "relative sigma" — noise=0.20 puts ~68 % of results within ±20 %.
     */
    private fun scatter(base: Double, noise: Double, rng: RandomSource?): Double {
        if (noise <= 0.0 || rng == null) return base
        var acc = 0.0
        repeat(4) { acc += rng.nextDouble() }
        val standardised = (acc - 2.0) / SCATTER_SIGMA_OF_IRWIN_HALL_4
        return base * (1.0 + noise * standardised)
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(SIZE_FACTOR, 1.0f)
    }

    override fun getDimensions(pose: Pose): EntityDimensions = super.getDimensions(pose).scale(sizeFactor)

    override fun getStandingEyeHeight(pose: Pose, dimensions: EntityDimensions): Float =
        dimensions.height * EYE_HEIGHT_RATIO

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putFloat(SIZE_FACTOR_TAG, sizeFactor)
        firstParentType?.let { tag.putString(FIRST_PARENT_TAG, it.toString()) }
        secondParentType?.let { tag.putString(SECOND_PARENT_TAG, it.toString()) }
        if (playerTruceTicks > 0) {
            playerTruceId?.let { tag.putUUID(PLAYER_TRUCE_ID_TAG, it) }
            tag.putInt(PLAYER_TRUCE_TICKS_TAG, playerTruceTicks)
        }
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        if (tag.contains(SIZE_FACTOR_TAG)) sizeFactor = tag.getFloat(SIZE_FACTOR_TAG)
        firstParentType = tag.getString(FIRST_PARENT_TAG).takeIf(String::isNotBlank)?.let(ResourceLocation::tryParse)
        secondParentType = tag.getString(SECOND_PARENT_TAG).takeIf(String::isNotBlank)?.let(ResourceLocation::tryParse)
        playerTruceId = if (tag.hasUUID(PLAYER_TRUCE_ID_TAG)) tag.getUUID(PLAYER_TRUCE_ID_TAG) else null
        playerTruceTicks = tag.getInt(PLAYER_TRUCE_TICKS_TAG).coerceIn(0, PLAYER_TRUCE_TICKS)
        refreshDimensions()
    }

    private class SlowTurningMoveControl(mob: Mob) : MoveControl(mob) {
        override fun rotlerp(sourceAngle: Float, targetAngle: Float, maximumChange: Float): Float =
            super.rotlerp(sourceAngle, targetAngle, min(maximumChange, MAX_BODY_TURN_PER_TICK))
    }

    companion object {
        private val SIZE_FACTOR: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(Chimera::class.java, EntityDataSerializers.FLOAT)
        private const val SIZE_FACTOR_TAG = "hexlove:chimera_size"
        private const val FIRST_PARENT_TAG = "hexlove:first_parent"
        private const val SECOND_PARENT_TAG = "hexlove:second_parent"
        private const val PLAYER_TRUCE_ID_TAG = "hexlove:player_truce_id"
        private const val PLAYER_TRUCE_TICKS_TAG = "hexlove:player_truce_ticks"
        private const val BASE_BODY_SIZE = (0.9 + 1.4) / 2.0
        private const val MIN_SIZE_FACTOR = 0.1f
        /** stddev of the sum of 4 U(0,1) — 1/sqrt(3) — used to normalise Irwin–Hall to unit sigma. */
        private const val SCATTER_SIGMA_OF_IRWIN_HALL_4 = 0.5773502691896258
        private const val PLAYER_TARGET_SCAN_INTERVAL = 80
        private const val PLAYER_TRUCE_TICKS = 20 * 20
        private const val MAX_BODY_TURN_PER_TICK = 6.0f
        private const val EYE_HEIGHT_RATIO = 0.82f
        private const val HEADBUTT_EVENT: Byte = 62
        private const val HEADBUTT_ANIMATION_TICKS = 14
        private val RAW_ANIMAL_MEAT: Set<Item> = setOf(
            Items.BEEF,
            Items.PORKCHOP,
            Items.MUTTON,
            Items.RABBIT,
            Items.CHICKEN,
            Items.COD,
            Items.SALMON,
            Items.TROPICAL_FISH,
            Items.PUFFERFISH,
        )
        private val RAW_ANIMAL_MEAT_INGREDIENT = Ingredient.of(*RAW_ANIMAL_MEAT.toTypedArray())

        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 4.0)
    }
}
