package io.github.teekas.hexlove.block.entity

import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import io.github.teekas.hexlove.block.WorldSoulAltarBlock
import io.github.teekas.hexlove.block.WorldSoulMultiblock
import io.github.teekas.hexlove.marriage.RingData
import io.github.teekas.hexlove.marriage.MarriageManager
import io.github.teekas.hexlove.menu.WorldSoulAltarMenu
import io.github.teekas.hexlove.registry.HexloveBlockEntities
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.worldsoul.WorldAffection
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class WorldSoulAltarBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(HexloveBlockEntities.WORLD_SOUL_ALTAR.value, pos, state), MenuProvider, Container {
    private val stacks: NonNullList<ItemStack> = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY)
    private var ritualStartedAt = 0L
    private var ritualOwner: UUID? = null
    private var ritualKind = RitualKind.NONE
    private var ritualAmount = 0
    private var candlePositions: List<BlockPos> = emptyList()
    private var structureProblem = WorldSoulMultiblock.Problem.DAIS

    val isRitualRunning: Boolean
        get() = ritualStartedAt > 0L && ritualOwner != null

    fun isStructureActive(): Boolean =
        blockState.hasProperty(WorldSoulAltarBlock.ACTIVE) && blockState.getValue(WorldSoulAltarBlock.ACTIVE)

    fun structureProblemCode(): Int = structureProblem.code

    fun ritualProgress(partialTick: Float = 0f): Float {
        if (!isRitualRunning) return 0f
        val now = level?.gameTime ?: return 0f
        return ((now - ritualStartedAt + partialTick).toFloat() / ritualDuration()).coerceIn(0f, 1f)
    }

    fun activeRitual(): RitualKind = ritualKind

    fun activeRitualAmount(): Int = ritualAmount

    /** Local item positions are shared with the server FX so rays end on the rendered offerings. */
    fun ringRitualPose(progress: Double): Vec3 = when (ritualKind) {
        RitualKind.ATTUNEMENT -> attunementRingPose(progress)
        RitualKind.DETACHMENT -> detachmentRingPose(progress)
        RitualKind.EMBODIMENT -> embodimentPose(progress, RING_REST, RING_HOVER)
        RitualKind.NONE -> RING_REST
    }

    fun amethystRitualPose(progress: Double): Vec3 = when (ritualKind) {
        RitualKind.EMBODIMENT -> embodimentPose(progress, AMETHYST_REST, AMETHYST_HOVER)
        else -> AMETHYST_REST
    }

    fun chargeMillis(): Int =
        (RingData.worldCharge(stacks[RING_SLOT]) * CHARGE_SCALE).roundToInt()
            .coerceIn(0, (RingData.MAX_WORLD_CHARGE * CHARGE_SCALE).toInt())

    fun isUsableBy(player: Player): Boolean =
        player.level() === level && player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= MENU_RADIUS * MENU_RADIUS

    fun canPlace(player: Player, slot: Int, stack: ItemStack): Boolean {
        if (isRitualSlotLocked(slot)) return false
        return when (slot) {
            AMETHYST_SLOT -> stack.`is`(Items.AMETHYST_SHARD)
            RING_SLOT -> stack.`is`(HexloveItems.SOULBOUND_RING.value) &&
                RingData.boundTo(stack) == player.uuid &&
                (RingData.worldSoul(stack) == null || RingData.isWorldLinked(stack, player.uuid))
            else -> false
        }
    }

    fun canTake(player: Player, slot: Int): Boolean = isUsableBy(player) && !isRitualSlotLocked(slot)

    fun startAttunement(player: Player): Boolean {
        val serverPlayer = player as? ServerPlayer ?: return false
        if (!refreshStructure(serverPlayer.serverLevel())) {
            message(serverPlayer, "screen.hexlove.world_soul_altar.structure_incomplete")
            return false
        }
        if (isRitualRunning) return false
        val ring = stacks[RING_SLOT]
        val linkedToOwner = RingData.isWorldLinked(ring, serverPlayer.uuid)
        if (!ring.`is`(HexloveItems.SOULBOUND_RING.value) ||
            RingData.boundTo(ring) != serverPlayer.uuid ||
            RingData.worldSoul(ring) != null && !linkedToOwner
        ) {
            message(serverPlayer, "screen.hexlove.world_soul_altar.needs_bound_ring")
            return false
        }
        ritualStartedAt = serverPlayer.serverLevel().gameTime
        ritualOwner = serverPlayer.uuid
        ritualKind = if (linkedToOwner) RitualKind.DETACHMENT else RitualKind.ATTUNEMENT
        ritualAmount = 0
        sync()
        serverPlayer.serverLevel().playSound(
            null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE,
            SoundSource.BLOCKS, 1.0f, if (linkedToOwner) 0.55f else 0.8f,
        )
        return true
    }

    fun embody(player: Player): Boolean {
        val serverPlayer = player as? ServerPlayer ?: return false
        if (!refreshStructure(serverPlayer.serverLevel())) {
            message(serverPlayer, "screen.hexlove.world_soul_altar.structure_incomplete")
            return false
        }
        if (isRitualRunning) return false
        val ring = stacks[RING_SLOT]
        if (!RingData.isWorldLinked(ring, serverPlayer.uuid) || RingData.boundTo(ring) != serverPlayer.uuid) {
            message(serverPlayer, "screen.hexlove.world_soul_altar.needs_linked_ring")
            return false
        }
        val count = minOf(
            stacks[AMETHYST_SLOT].count,
            floor(RingData.worldCharge(ring) + 1.0e-7).toInt(),
            HexloveItems.CRYSTALLIZED_AFFECTION.value.maxStackSize,
        )
        if (count <= 0) {
            message(serverPlayer, "screen.hexlove.world_soul_altar.not_enough_affection")
            return false
        }
        ritualStartedAt = serverPlayer.serverLevel().gameTime
        ritualOwner = serverPlayer.uuid
        ritualKind = RitualKind.EMBODIMENT
        ritualAmount = count
        sync()
        serverPlayer.serverLevel().playSound(
            null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE,
            SoundSource.BLOCKS, 1.0f, 0.62f,
        )
        return true
    }

    override fun getDisplayName(): Component = Component.translatable("block.hexlove.world_soul_altar")

    override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
        WorldSoulAltarMenu(containerId, inventory, this)

    override fun getContainerSize(): Int = SLOT_COUNT

    override fun isEmpty(): Boolean = stacks.all(ItemStack::isEmpty)

    override fun getItem(slot: Int): ItemStack = stacks.getOrElse(slot) { ItemStack.EMPTY }

    override fun removeItem(slot: Int, amount: Int): ItemStack {
        if (slot !in stacks.indices || isRitualSlotLocked(slot)) return ItemStack.EMPTY
        val removed = ContainerHelper.removeItem(stacks, slot, amount)
        if (!removed.isEmpty) {
            if (slot == RING_SLOT) cancelRitual()
            sync()
        }
        return removed
    }

    override fun removeItemNoUpdate(slot: Int): ItemStack {
        if (slot !in stacks.indices || isRitualSlotLocked(slot)) return ItemStack.EMPTY
        val removed = stacks.set(slot, ItemStack.EMPTY)
        if (slot == RING_SLOT) cancelRitual()
        return removed
    }

    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot !in stacks.indices || isRitualSlotLocked(slot)) return
        stacks[slot] = stack.copyWithCount(stack.count.coerceAtMost(getMaxStackSizeForSlot(slot)))
        if (slot == RING_SLOT) cancelRitual()
        sync()
    }

    override fun stillValid(player: Player): Boolean = isUsableBy(player)

    override fun clearContent() {
        stacks.fill(ItemStack.EMPTY)
        cancelRitual()
        sync()
    }

    override fun saveAdditional(tag: CompoundTag) {
        super.saveAdditional(tag)
        ContainerHelper.saveAllItems(tag, stacks)
        tag.putLong(RITUAL_STARTED_TAG, ritualStartedAt)
        ritualOwner?.let { tag.putUUID(RITUAL_OWNER_TAG, it) }
        tag.putInt(RITUAL_KIND_TAG, ritualKind.id)
        tag.putInt(RITUAL_AMOUNT_TAG, ritualAmount)
    }

    override fun load(tag: CompoundTag) {
        super.load(tag)
        stacks.fill(ItemStack.EMPTY)
        ContainerHelper.loadAllItems(tag, stacks)
        ritualStartedAt = tag.getLong(RITUAL_STARTED_TAG)
        ritualOwner = if (tag.hasUUID(RITUAL_OWNER_TAG)) tag.getUUID(RITUAL_OWNER_TAG) else null
        ritualKind = if (tag.contains(RITUAL_KIND_TAG)) {
            RitualKind.fromId(tag.getInt(RITUAL_KIND_TAG))
        } else if (ritualStartedAt > 0L && ritualOwner != null) {
            // Saves made before embodiment had only one possible ritual.
            RitualKind.ATTUNEMENT
        } else {
            RitualKind.NONE
        }
        ritualAmount = tag.getInt(RITUAL_AMOUNT_TAG).coerceIn(0, HexloveItems.CRYSTALLIZED_AFFECTION.value.maxStackSize)
        if (ritualStartedAt <= 0L || ritualOwner == null || ritualKind == RitualKind.NONE) cancelRitual()
    }

    override fun getUpdateTag(): CompoundTag = CompoundTag().also(::saveAdditional)

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    private fun refreshStructure(level: ServerLevel): Boolean {
        val inspection = WorldSoulMultiblock.inspect(level, worldPosition)
        val result = inspection.result
        structureProblem = inspection.problem
        candlePositions = result?.candles ?: emptyList()
        val active = result != null
        if (blockState.getValue(WorldSoulAltarBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, blockState.setValue(WorldSoulAltarBlock.ACTIVE, active), 3)
        }
        if (!active && isRitualRunning) {
            cancelRitual()
            sync()
        }
        return active
    }

    private fun finishAttunement(level: ServerLevel) {
        val ownerId = ritualOwner ?: return cancelRitual()
        val player = level.server.playerList.getPlayer(ownerId)
        val ring = stacks[RING_SLOT]
        val firstAttunement = !RingData.hasWorldAttunementHistory(ring)
        if (player == null || !isUsableBy(player) ||
            RingData.boundTo(ring) != ownerId ||
            !RingData.linkToWorld(ring, ownerId)
        ) {
            cancelRitual()
            sync()
            return
        }
        if (firstAttunement) {
            blessThroughOfferedRing(player, ring, WorldAffection.INITIAL_EMBRACE_TICKS)
        }
        HexloveAdvancements.grant(player, HexloveAdvancements.HEART_OF_THE_WORLD)
        player.sendSystemMessage(
            Component.translatable(
                if (firstAttunement) "screen.hexlove.world_soul_altar.world_greeting"
                else "screen.hexlove.world_soul_altar.world_return",
            ),
        )
        cancelRitual()
        val landing = localToWorld(RING_REST)
        level.sendParticles(PINK, landing.x, landing.y, landing.z, 14, 0.18, 0.10, 0.18, 0.025)
        level.sendParticles(ParticleTypes.END_ROD, landing.x, landing.y, landing.z, 5, 0.12, 0.08, 0.12, 0.018)
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.7f)
        sync()
    }

    private fun finishDetachment(level: ServerLevel) {
        val ownerId = ritualOwner ?: return cancelRitual()
        val player = level.server.playerList.getPlayer(ownerId)
        val ring = stacks[RING_SLOT]
        if (player == null || !isUsableBy(player) ||
            RingData.boundTo(ring) != ownerId ||
            !RingData.unlinkFromWorld(ring, ownerId)
        ) {
            cancelRitual()
            sync()
            return
        }
        player.sendSystemMessage(Component.translatable("screen.hexlove.world_soul_altar.world_parting"))
        cancelRitual()
        val landing = localToWorld(RING_REST)
        level.sendParticles(VIOLET, landing.x, landing.y, landing.z, 18, 0.20, 0.11, 0.20, 0.022)
        level.sendParticles(PALE, landing.x, landing.y, landing.z, 7, 0.13, 0.09, 0.13, 0.014)
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.75f, 0.62f)
        sync()
    }

    private fun finishEmbodiment(level: ServerLevel) {
        val ownerId = ritualOwner ?: return cancelRitual()
        val player = level.server.playerList.getPlayer(ownerId)
        val ring = stacks[RING_SLOT]
        val count = ritualAmount
        if (player == null || !isUsableBy(player) || count <= 0 ||
            RingData.boundTo(ring) != ownerId || !RingData.isWorldLinked(ring, ownerId) ||
            stacks[AMETHYST_SLOT].count < count ||
            floor(RingData.worldCharge(ring) + 1.0e-7).toInt() < count ||
            !RingData.spendWorldCharge(ring, count)
        ) {
            cancelRitual()
            sync()
            return
        }

        stacks[AMETHYST_SLOT].shrink(count)
        if (stacks[AMETHYST_SLOT].isEmpty) stacks[AMETHYST_SLOT] = ItemStack.EMPTY
        val result = ItemStack(HexloveItems.CRYSTALLIZED_AFFECTION.value, count)
        if (!player.addItem(result)) Block.popResource(level, worldPosition, result)
        blessThroughOfferedRing(player, ring, count * WorldAffection.CRYSTAL_EMBRACE_TICKS)
        player.sendSystemMessage(Component.translatable("screen.hexlove.world_soul_altar.embodied", count))
        cancelRitual()

        val ringLanding = localToWorld(RING_REST)
        val crystalLanding = localToWorld(AMETHYST_REST)
        level.sendParticles(ROSE, crystalLanding.x, crystalLanding.y, crystalLanding.z, 16, 0.20, 0.11, 0.20, 0.025)
        level.sendParticles(RED, ringLanding.x, ringLanding.y, ringLanding.z, 9, 0.14, 0.08, 0.14, 0.018)
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 1.85f)
        sync()
    }

    private fun ritualParticles(level: ServerLevel) {
        val age = (level.gameTime - ritualStartedAt).coerceAtLeast(0L).toInt()
        when (ritualKind) {
            RitualKind.ATTUNEMENT -> attunementParticles(level, age)
            RitualKind.DETACHMENT -> detachmentParticles(level, age)
            RitualKind.EMBODIMENT -> embodimentParticles(level, age)
            RitualKind.NONE -> Unit
        }
    }

    private fun attunementParticles(level: ServerLevel, age: Int) {
        val progress = (age.toDouble() / ATTUNEMENT_TICKS).coerceIn(0.0, 1.0)
        val ring = localToWorld(ringRitualPose(progress))

        if (progress < ATTUNEMENT_FALL_START) {
            candlePositions.forEachIndexed { index, candle ->
                val flame = Vec3.atCenterOf(candle).add(0.0, 0.42, 0.0)
                drawCandleFlow(level, flame, ring, index, age, progress > 0.12)
            }
        }
        if (progress in SPHERE_START..SPHERE_END) drawCollapsingSphere(level, ring, progress, age)

        if (age == ATTUNEMENT_SPHERE_SOUND_TICK) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.75f, 1.65f)
        }
        if (age == ATTUNEMENT_BURST_TICK) attunementBurst(level, ring)
        if (progress >= ATTUNEMENT_FALL_START) {
            level.sendParticles(PALE, ring.x, ring.y + 0.08, ring.z, 2, 0.10, 0.12, 0.10, 0.012)
        }
    }

    private fun detachmentParticles(level: ServerLevel, age: Int) {
        val progress = (age.toDouble() / DETACHMENT_TICKS).coerceIn(0.0, 1.0)
        val ring = localToWorld(ringRitualPose(progress))

        if (progress < DETACHMENT_FALL_START) {
            candlePositions.forEachIndexed { index, candle ->
                val flame = Vec3.atCenterOf(candle).add(0.0, 0.42, 0.0)
                // Inverse candle streams make the stored world bond visibly leave the ring.
                drawCandleFlow(level, ring, flame, index, age, progress > 0.10)
            }
        }
        if (progress in DETACHMENT_SPHERE_START..DETACHMENT_SPHERE_END) {
            drawExpandingWorldShell(level, ring, progress, age)
        }

        if (age == DETACHMENT_RELEASE_SOUND_TICK) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.45f)
        }
        if (age == DETACHMENT_BURST_TICK) detachmentBurst(level, ring)
        if (progress >= DETACHMENT_FALL_START) {
            level.sendParticles(VIOLET, ring.x, ring.y + 0.07, ring.z, 2, 0.11, 0.10, 0.11, 0.010)
            level.sendParticles(PALE, ring.x, ring.y, ring.z, 1, 0.07, 0.07, 0.07, 0.006)
        }
    }

    private fun embodimentParticles(level: ServerLevel, age: Int) {
        val progress = (age.toDouble() / EMBODIMENT_TICKS).coerceIn(0.0, 1.0)
        val ringLocal = ringRitualPose(progress)
        val amethystLocal = amethystRitualPose(progress)
        val ring = localToWorld(ringLocal)
        val amethyst = localToWorld(amethystLocal)

        if (progress < EMBODIMENT_FALL_START) {
            candlePositions.forEachIndexed { index, candle ->
                val flame = Vec3.atCenterOf(candle).add(0.0, 0.42, 0.0)
                val target = if (index % 2 == 0) ring else amethyst
                drawCandleFlow(level, flame, target, index, age, true)
            }
        }

        if (progress in TRANSFER_START..TRANSFER_END) {
            drawRingToCrystalTransfer(level, ring, amethyst, ringLocal, age, progress)
        }
        if (progress >= TRANSFORM_GLOW_START && progress < EMBODIMENT_FALL_START) {
            val spread = 0.12 + (progress - TRANSFORM_GLOW_START) * 0.35
            level.sendParticles(ROSE, amethyst.x, amethyst.y, amethyst.z, 3, spread, spread, spread, 0.012)
            level.sendParticles(RED, amethyst.x, amethyst.y, amethyst.z, 2, spread * 0.65, spread * 0.65, spread * 0.65, 0.008)
        }

        if (age == EMBODIMENT_TRANSFER_SOUND_TICK) {
            level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8f, 0.72f)
        }
        if (age == EMBODIMENT_TRANSFORM_TICK) embodimentTransformBurst(level, amethyst)
        if (progress >= EMBODIMENT_FALL_START) {
            level.sendParticles(ROSE, amethyst.x, amethyst.y, amethyst.z, 2, 0.12, 0.12, 0.12, 0.012)
            level.sendParticles(RED, ring.x, ring.y, ring.z, 1, 0.08, 0.08, 0.08, 0.008)
        }
    }

    /** Sparse moving motes replace the old equally spaced dotted line; periodic pulses make a ray. */
    private fun drawCandleFlow(
        level: ServerLevel,
        origin: Vec3,
        target: Vec3,
        candleIndex: Int,
        age: Int,
        allowPulse: Boolean,
    ) {
        val delta = target.subtract(origin)
        val horizontal = sqrt(delta.x * delta.x + delta.z * delta.z).coerceAtLeast(1.0e-4)
        val side = Vec3(-delta.z / horizontal, 0.0, delta.x / horizontal)
        val seed = candleIndex * 1.731
        val baseBend = side.scale(0.16 + candleIndex * 0.035).add(0.0, 0.18 + 0.04 * sin(seed), 0.0)
        val phases = FLOW_PHASES
        for (mote in phases.indices) {
            val speed = 0.016 + mote * 0.0017 + candleIndex * 0.0006
            val t = (age * speed + phases[mote] + candleIndex * 0.071) % 1.0
            val bend = baseBend.scale(if (mote % 2 == 0) 1.0 else -0.72)
            val point = curvedPoint(origin, target, t, bend, seed + mote * 0.83)
            val colour = when ((mote + candleIndex) % 5) {
                0 -> PALE
                1, 2 -> PINK
                else -> VIOLET
            }
            level.sendParticles(colour, point.x, point.y, point.z, if (mote == 0) 2 else 1, 0.012, 0.012, 0.012, 0.0)
        }

        if (!allowPulse) return
        val shiftedAge = age + candleIndex * 7
        val pulseAge = shiftedAge % CANDLE_PULSE_PERIOD
        if (pulseAge >= CANDLE_PULSE_TICKS) return
        val cycle = shiftedAge / CANDLE_PULSE_PERIOD
        val direction = if ((cycle + candleIndex) % 2 == 0) 1.0 else -1.0
        val bend = side.scale(direction * (0.46 + 0.11 * sin(cycle * 1.93 + seed)))
            .add(0.0, 0.30 + 0.12 * cos(cycle * 1.37 + seed), 0.0)
        drawCurvedPulse(
            level, origin, target, pulseAge, CANDLE_PULSE_TICKS,
            if ((cycle + candleIndex) % 3 == 0) RED else ROSE,
            PALE, bend, 0.34, seed + cycle,
        )
    }

    private fun drawCollapsingSphere(level: ServerLevel, centre: Vec3, progress: Double, age: Int) {
        val sphereProgress = ((progress - SPHERE_START) / (SPHERE_END - SPHERE_START)).coerceIn(0.0, 1.0)
        val contraction = smoothstep(((sphereProgress - 0.52) / 0.48).coerceIn(0.0, 1.0))
        val radius = (0.86 + sin(age * 0.19) * 0.055) * (1.0 - contraction) + 0.045
        val rotation = age * 0.105

        repeat(SPHERE_POINTS) { index ->
            val y = 1.0 - 2.0 * (index + 0.5) / SPHERE_POINTS
            val ringRadius = sqrt((1.0 - y * y).coerceAtLeast(0.0))
            val angle = index * GOLDEN_ANGLE + rotation
            val point = centre.add(cos(angle) * ringRadius * radius, y * radius, sin(angle) * ringRadius * radius)
            val colour = when ((index + age / 2) % 7) {
                0 -> PALE
                in 1..3 -> VIOLET
                else -> PINK
            }
            level.sendParticles(colour, point.x, point.y, point.z, 1, 0.008, 0.008, 0.008, 0.0)
        }

        // A few shell motes receive real inward velocity, making the sphere visibly enter the ring.
        repeat(4) { index ->
            val angle = age * 0.17 + index * PI / 2.0
            val y = sin(age * 0.11 + index * 1.7) * radius * 0.62
            val origin = centre.add(cos(angle) * radius, y, sin(angle) * radius)
            val velocity = centre.subtract(origin).normalize().scale(0.035 + contraction * 0.035)
            level.sendParticles(PALE, origin.x, origin.y, origin.z, 0, velocity.x, velocity.y, velocity.z, 1.0)
        }
    }

    private fun drawExpandingWorldShell(level: ServerLevel, centre: Vec3, progress: Double, age: Int) {
        val shellProgress = smoothstep(
            ((progress - DETACHMENT_SPHERE_START) /
                (DETACHMENT_SPHERE_END - DETACHMENT_SPHERE_START)).coerceIn(0.0, 1.0),
        )
        val radius = 0.08 + shellProgress * 0.94 + sin(age * 0.21) * 0.035
        val rotation = -age * 0.13
        val points = (DETACHMENT_SPHERE_POINTS * (1.0 - shellProgress * 0.38)).roundToInt()

        repeat(points) { index ->
            val y = 1.0 - 2.0 * (index + 0.5) / points
            val ringRadius = sqrt((1.0 - y * y).coerceAtLeast(0.0))
            val angle = index * GOLDEN_ANGLE + rotation
            val fracture = if ((index + age / 3) % 5 == 0) shellProgress * 0.16 else 0.0
            val point = centre.add(
                cos(angle) * ringRadius * (radius + fracture),
                y * (radius + fracture * 0.6),
                sin(angle) * ringRadius * (radius + fracture),
            )
            val colour = when ((index + age / 2) % 8) {
                0, 1 -> PALE
                in 2..4 -> VIOLET
                else -> PINK
            }
            level.sendParticles(colour, point.x, point.y, point.z, 1, 0.008, 0.008, 0.008, 0.0)
        }

        // Real outward velocity makes the final shell visibly tear away instead of merely fading.
        repeat(5) { index ->
            val angle = -age * 0.18 + index * (2.0 * PI / 5.0)
            val origin = centre.add(cos(angle) * radius, sin(age * 0.12 + index) * radius * 0.55, sin(angle) * radius)
            val velocity = origin.subtract(centre).normalize().scale(0.025 + shellProgress * 0.045)
            level.sendParticles(PALE, origin.x, origin.y, origin.z, 0, velocity.x, velocity.y, velocity.z, 1.0)
        }
    }

    private fun drawRingToCrystalTransfer(
        level: ServerLevel,
        ring: Vec3,
        amethyst: Vec3,
        ringLocal: Vec3,
        age: Int,
        progress: Double,
    ) {
        repeat(3) { strand ->
            val shiftedAge = age + strand * 5
            val pulseAge = shiftedAge % TRANSFER_PULSE_TICKS
            val cycle = shiftedAge / TRANSFER_PULSE_TICKS
            val delta = amethyst.subtract(ring)
            val horizontal = sqrt(delta.x * delta.x + delta.z * delta.z).coerceAtLeast(1.0e-4)
            val side = Vec3(-delta.z / horizontal, 0.0, delta.x / horizontal)
            val direction = if ((cycle + strand) % 2 == 0) 1.0 else -1.0
            val bend = side.scale(direction * (0.17 + strand * 0.075))
                .add(0.0, 0.16 + strand * 0.055, 0.0)
            drawCurvedPulse(
                level, ring, amethyst, pulseAge, TRANSFER_PULSE_TICKS,
                if (strand == 1) ROSE else RED,
                if (strand == 1) PALE else PINK,
                bend, 0.62, strand * 2.19 + cycle,
            )
        }

        if (age % 2 == 0) {
            val intensity = smoothstep(((progress - TRANSFER_START) / 0.18).coerceIn(0.0, 1.0))
            drawHexSeal(level, ringLocal, age, 0.25 + intensity * 0.11)
        }
        level.sendParticles(RED, amethyst.x, amethyst.y, amethyst.z, 2, 0.08, 0.08, 0.08, 0.008)
    }

    private fun drawCurvedPulse(
        level: ServerLevel,
        origin: Vec3,
        target: Vec3,
        pulseAge: Int,
        duration: Int,
        beam: ConjureParticleOptions,
        core: ConjureParticleOptions,
        bend: Vec3,
        trailLength: Double,
        twist: Double,
    ) {
        val head = smoothstep(((pulseAge + 1.0) / duration).coerceIn(0.0, 1.0))
        repeat(PULSE_TRAIL_POINTS) { index ->
            val trail = (index.toDouble() / (PULSE_TRAIL_POINTS - 1)).pow(1.42) * trailLength
            val t = head - trail
            if (t < 0.0) return@repeat
            val point = curvedPoint(origin, target, t, bend, twist)
            val colour = if (index < 3) core else beam
            level.sendParticles(colour, point.x, point.y, point.z, if (index == 0) 3 else 1, 0.012, 0.012, 0.012, 0.0)
        }
    }

    private fun drawHexSeal(level: ServerLevel, centre: Vec3, age: Int, radius: Double) {
        val rotation = age * 0.075
        val vertices = Array(6) { index ->
            val angle = rotation + index * PI / 3.0
            localToWorld(Vec3(centre.x + cos(angle) * radius, centre.y + sin(angle) * radius, centre.z))
        }
        for (index in vertices.indices) {
            val from = vertices[index]
            val to = vertices[(index + 1) % vertices.size]
            repeat(3) { edgePoint ->
                val point = from.lerp(to, edgePoint / 3.0)
                level.sendParticles(if (index % 2 == 0) RED else ROSE, point.x, point.y, point.z, 1, 0.006, 0.006, 0.006, 0.0)
            }
        }
    }

    private fun attunementBurst(level: ServerLevel, centre: Vec3) {
        level.sendParticles(PINK, centre.x, centre.y, centre.z, 78, 0.70, 0.72, 0.70, 0.085)
        level.sendParticles(VIOLET, centre.x, centre.y, centre.z, 56, 0.55, 0.58, 0.55, 0.072)
        level.sendParticles(PALE, centre.x, centre.y, centre.z, 34, 0.38, 0.42, 0.38, 0.055)
        level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, 30, 0.45, 0.48, 0.45, 0.058)
        level.sendParticles(ParticleTypes.FLASH, centre.x, centre.y, centre.z, 1, 0.0, 0.0, 0.0, 0.0)
        level.playSound(null, worldPosition, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.15f, 1.25f)
    }

    private fun detachmentBurst(level: ServerLevel, centre: Vec3) {
        level.sendParticles(VIOLET, centre.x, centre.y, centre.z, 62, 0.76, 0.66, 0.76, 0.078)
        level.sendParticles(PINK, centre.x, centre.y, centre.z, 38, 0.58, 0.50, 0.58, 0.060)
        level.sendParticles(PALE, centre.x, centre.y, centre.z, 28, 0.42, 0.38, 0.42, 0.050)
        level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, 20, 0.46, 0.42, 0.46, 0.048)
        level.sendParticles(ParticleTypes.FLASH, centre.x, centre.y, centre.z, 1, 0.0, 0.0, 0.0, 0.0)
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, 0.72f)
    }

    private fun embodimentTransformBurst(level: ServerLevel, centre: Vec3) {
        val amountGlow = ritualAmount.coerceAtMost(16)
        level.sendParticles(RED, centre.x, centre.y, centre.z, 42 + amountGlow, 0.46, 0.48, 0.46, 0.065)
        level.sendParticles(ROSE, centre.x, centre.y, centre.z, 36 + amountGlow, 0.38, 0.42, 0.38, 0.052)
        level.sendParticles(VIOLET, centre.x, centre.y, centre.z, 22, 0.30, 0.34, 0.30, 0.042)
        level.sendParticles(PALE, centre.x, centre.y, centre.z, 20, 0.22, 0.26, 0.22, 0.036)
        level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, 18, 0.28, 0.32, 0.28, 0.043)
        level.sendParticles(ParticleTypes.FLASH, centre.x, centre.y, centre.z, 1, 0.0, 0.0, 0.0, 0.0)
        level.playSound(null, worldPosition, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.0f, 0.92f)
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 1.35f)
    }

    private fun curvedPoint(origin: Vec3, target: Vec3, progress: Double, bend: Vec3, twist: Double): Vec3 {
        val t = progress.coerceIn(0.0, 1.0)
        val envelope = 4.0 * t * (1.0 - t)
        val curl = sin(t * PI * 3.0 + twist) * 0.045 * envelope
        return origin.lerp(target, t).add(bend.scale(envelope)).add(0.0, curl, 0.0)
    }

    private fun localToWorld(local: Vec3): Vec3 {
        val dx = local.x - 0.5
        val dz = local.z - 0.5
        val (rotatedX, rotatedZ) = when (blockState.getValue(WorldSoulAltarBlock.FACING)) {
            net.minecraft.core.Direction.EAST -> dz to -dx
            net.minecraft.core.Direction.SOUTH -> -dx to -dz
            net.minecraft.core.Direction.WEST -> -dz to dx
            else -> dx to dz
        }
        return Vec3(
            worldPosition.x + 0.5 + rotatedX,
            worldPosition.y + local.y,
            worldPosition.z + 0.5 + rotatedZ,
        )
    }

    private fun attunementRingPose(progress: Double): Vec3 {
        val lift = smoothstep((progress / ATTUNEMENT_LIFT_END).coerceIn(0.0, 1.0))
        val fall = smoothstep(
            ((progress - ATTUNEMENT_FALL_START) / (1.0 - ATTUNEMENT_FALL_START)).coerceIn(0.0, 1.0),
        )
        val airborne = RING_REST.lerp(ATTUNEMENT_HOVER, lift)
        return airborne.lerp(RING_REST, fall).add(0.0, sin(fall * PI) * 0.055, 0.0)
    }

    private fun detachmentRingPose(progress: Double): Vec3 {
        val lift = smoothstep((progress / DETACHMENT_LIFT_END).coerceIn(0.0, 1.0))
        val fall = smoothstep(
            ((progress - DETACHMENT_FALL_START) / (1.0 - DETACHMENT_FALL_START)).coerceIn(0.0, 1.0),
        )
        val airborne = RING_REST.lerp(DETACHMENT_HOVER, lift).lerp(RING_REST, fall)
        val unbinding = smoothstep(((progress - 0.24) / 0.60).coerceIn(0.0, 1.0)) * (1.0 - fall)
        return airborne.add(
            sin(progress * PI * 4.0) * 0.10 * unbinding,
            sin(progress * PI * 3.0) * 0.045 * unbinding + sin(fall * PI) * 0.055,
            cos(progress * PI * 4.0) * 0.10 * unbinding,
        )
    }

    private fun embodimentPose(progress: Double, rest: Vec3, hover: Vec3): Vec3 {
        val lift = smoothstep((progress / EMBODIMENT_LIFT_END).coerceIn(0.0, 1.0))
        val fall = smoothstep(
            ((progress - EMBODIMENT_FALL_START) / (1.0 - EMBODIMENT_FALL_START)).coerceIn(0.0, 1.0),
        )
        val airborne = rest.lerp(hover, lift)
        return airborne.lerp(rest, fall).add(0.0, sin(fall * PI) * 0.065, 0.0)
    }

    private fun ritualDuration(): Long = when (ritualKind) {
        RitualKind.EMBODIMENT -> EMBODIMENT_TICKS
        RitualKind.DETACHMENT -> DETACHMENT_TICKS
        else -> ATTUNEMENT_TICKS
    }

    private fun isRitualSlotLocked(slot: Int): Boolean = isRitualRunning &&
        (slot == RING_SLOT || ritualKind == RitualKind.EMBODIMENT && slot == AMETHYST_SLOT)

    private fun ritualStillValid(level: ServerLevel): Boolean {
        val ownerId = ritualOwner ?: return false
        val player = level.server.playerList.getPlayer(ownerId) ?: return false
        if (!isUsableBy(player)) return false
        val ring = stacks[RING_SLOT]
        if (RingData.boundTo(ring) != ownerId) return false
        return when (ritualKind) {
            RitualKind.ATTUNEMENT -> !RingData.isWorldLinked(ring)
            RitualKind.DETACHMENT -> RingData.isWorldLinked(ring, ownerId)
            RitualKind.EMBODIMENT -> ritualAmount > 0 &&
                RingData.isWorldLinked(ring, ownerId) &&
                stacks[AMETHYST_SLOT].count >= ritualAmount &&
                floor(RingData.worldCharge(ring) + 1.0e-7).toInt() >= ritualAmount
            RitualKind.NONE -> false
        }
    }

    private fun smoothstep(value: Double): Double = value * value * (3.0 - 2.0 * value)

    /**
     * Moving a married ring into this altar briefly suspends MarriageManager's inventory benefit.
     * The offered ring and authoritative marriage record still prove the vow, so its owner does
     * not lose the promised half-strength world blessing during the very rite that needs the ring.
     */
    private fun blessThroughOfferedRing(player: ServerPlayer, ring: ItemStack, ticks: Int) {
        WorldAffection.bless(player, ticks, shareWithSpouse = false)
        val spouseId = RingData.spouse(ring) ?: return
        val record = MarriageManager.record(player.server, player.uuid) ?: return
        if (!record.contains(player.uuid) || record.other(player.uuid) != spouseId ||
            !RingData.matches(ring, player.uuid, spouseId)
        ) return
        val spouse = player.server.playerList.getPlayer(spouseId) ?: return
        if (!MarriageManager.carriesOwnRing(player.server, spouse)) return
        WorldAffection.bless(spouse, ticks / 2, shareWithSpouse = false)
    }

    private fun getMaxStackSizeForSlot(slot: Int): Int = if (slot == RING_SLOT) 1 else maxStackSize

    private fun cancelRitual() {
        ritualStartedAt = 0L
        ritualOwner = null
        ritualKind = RitualKind.NONE
        ritualAmount = 0
    }

    private fun message(player: ServerPlayer, key: String) {
        player.sendSystemMessage(Component.translatable(key))
    }

    private fun sync() {
        super.setChanged()
        level?.sendBlockUpdated(worldPosition, blockState, blockState, 3)
    }

    enum class RitualKind(val id: Int) {
        NONE(0),
        ATTUNEMENT(1),
        EMBODIMENT(2),
        DETACHMENT(3);

        companion object {
            fun fromId(id: Int): RitualKind = entries.firstOrNull { it.id == id } ?: NONE
        }
    }

    companion object {
        const val AMETHYST_SLOT = 0
        const val RING_SLOT = 1
        const val SLOT_COUNT = 2
        const val CHARGE_SCALE = 1_000.0
        const val PROGRESS_SCALE = 1_000
        private const val MENU_RADIUS = 7.0
        private const val RITUAL_STARTED_TAG = "ritual_started"
        private const val RITUAL_OWNER_TAG = "ritual_owner"
        private const val RITUAL_KIND_TAG = "ritual_kind"
        private const val RITUAL_AMOUNT_TAG = "ritual_amount"

        private const val ATTUNEMENT_TICKS = 120L
        private const val ATTUNEMENT_LIFT_END = 0.25
        private const val SPHERE_START = 0.27
        private const val SPHERE_END = 0.88
        private const val ATTUNEMENT_FALL_START = 0.90
        private const val ATTUNEMENT_SPHERE_SOUND_TICK = 38
        private const val ATTUNEMENT_BURST_TICK = 108

        private const val DETACHMENT_TICKS = 120L
        private const val DETACHMENT_LIFT_END = 0.22
        private const val DETACHMENT_SPHERE_START = 0.24
        private const val DETACHMENT_SPHERE_END = 0.88
        private const val DETACHMENT_FALL_START = 0.90
        private const val DETACHMENT_RELEASE_SOUND_TICK = 34
        private const val DETACHMENT_BURST_TICK = 104

        private const val EMBODIMENT_TICKS = 150L
        private const val EMBODIMENT_LIFT_END = 0.28
        private const val TRANSFER_START = 0.36
        private const val TRANSFER_END = 0.86
        private const val TRANSFORM_GLOW_START = 0.72
        private const val EMBODIMENT_FALL_START = 0.90
        private const val EMBODIMENT_TRANSFER_SOUND_TICK = 54
        private const val EMBODIMENT_TRANSFORM_TICK = 132

        private const val CANDLE_PULSE_PERIOD = 29
        private const val CANDLE_PULSE_TICKS = 9
        private const val TRANSFER_PULSE_TICKS = 13
        private const val PULSE_TRAIL_POINTS = 12
        private const val SPHERE_POINTS = 24
        private const val DETACHMENT_SPHERE_POINTS = 30
        private const val GOLDEN_ANGLE = 2.399963229728653
        private val FLOW_PHASES = doubleArrayOf(0.02, 0.11, 0.29, 0.58, 0.83)

        private val RING_REST = Vec3(0.30, 1.075, 0.32)
        private val AMETHYST_REST = Vec3(0.70, 1.035, 0.32)
        private val ATTUNEMENT_HOVER = Vec3(0.50, 2.14, 0.50)
        private val DETACHMENT_HOVER = Vec3(0.50, 2.20, 0.50)
        private val RING_HOVER = Vec3(0.17, 2.04, 0.52)
        private val AMETHYST_HOVER = Vec3(0.83, 1.94, 0.48)

        private val PINK = ConjureParticleOptions(0xFF67BA)
        private val VIOLET = ConjureParticleOptions(0xC384FF)
        private val RED = ConjureParticleOptions(0xFF304F)
        private val ROSE = ConjureParticleOptions(0xFF5F91)
        private val PALE = ConjureParticleOptions(0xFFF2FA)

        @JvmStatic
        fun tick(level: ServerLevel, pos: BlockPos, state: BlockState, altar: WorldSoulAltarBlockEntity) {
            if (level.gameTime % STRUCTURE_CHECK_INTERVAL == 0L ||
                altar.isRitualRunning && altar.candlePositions.isEmpty()
            ) altar.refreshStructure(level)
            if (!altar.isRitualRunning) return
            if (!altar.isStructureActive() || !altar.ritualStillValid(level)) {
                altar.cancelRitual()
                altar.sync()
                return
            }
            altar.ritualParticles(level)
            if (level.gameTime - altar.ritualStartedAt >= altar.ritualDuration()) {
                when (altar.ritualKind) {
                    RitualKind.ATTUNEMENT -> altar.finishAttunement(level)
                    RitualKind.DETACHMENT -> altar.finishDetachment(level)
                    RitualKind.EMBODIMENT -> altar.finishEmbodiment(level)
                    RitualKind.NONE -> Unit
                }
            }
        }

        private const val STRUCTURE_CHECK_INTERVAL = 20L
    }
}
