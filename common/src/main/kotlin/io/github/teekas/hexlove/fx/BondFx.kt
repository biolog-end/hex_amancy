package io.github.teekas.hexlove.fx

import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import io.github.teekas.hexlove.config.HexloveServerConfig
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Everything the bonds of this mod look like.
 *
 * The particle itself is Hex Casting's own `conjure_particle`: it is drawn additively, so a colour
 * given to it reads as *glow* rather than as paint, which is exactly the look the rest of the mod's
 * spells already have. Only the colour changes between effects — pink for love, red for jealousy,
 * blue for the song.
 *
 * Two kinds of effect live here:
 *
 * - **one-shot**, at the moment of casting: an arrow of light flies from the beloved to the one who
 *   just fell for them and bursts into a ring around them. Because that takes about a second of game
 *   time, it cannot be drawn from inside a spell; [tick] advances the ones in flight instead.
 * - **ambient**, for as long as the bond lasts: a very small number of motes leaving the bonded
 *   creature, so that "this cow is charmed" is readable across a field. A permanent charm can sit in
 *   a base forever, so the love version is deliberately the thinnest of all of them.
 */
object BondFx {
    /** Love: hot pink core, pale pink ring. */
    private val LOVE = ConjureParticleOptions(0xFF5FB0)
    private val LOVE_PALE = ConjureParticleOptions(0xFFAEDC)

    /** Jealousy: the same shape, in blood. */
    private val JEALOUSY = ConjureParticleOptions(0xFF2A2A)
    private val JEALOUSY_PALE = ConjureParticleOptions(0xFF7060)

    /** The song: cold blue, the colour of something calling from far away. */
    private val SIREN = ConjureParticleOptions(0x5FC6FF)

    /** Pheromones are nearly white at the centre, with a blush around every mote. */
    private val PHEROMONE = ConjureParticleOptions(0xFFB8DF)
    private val PHEROMONE_CORE = ConjureParticleOptions(0xFFF8FF)

    /** A phantom is brighter than ordinary affection: a visible idea, not a living body. */
    private val PHANTOM = ConjureParticleOptions(0xFF78C8)
    private val PHANTOM_CORE = ConjureParticleOptions(0xFFE9FA)

    /** Tithe is not another love arrow: this is a dark-red thread being pulled back into the caster. */
    private val TITHE = ConjureParticleOptions(0x8D1025)
    private val TITHE_PALE = ConjureParticleOptions(0xE45A68)
    private val TITHE_HEAL = ConjureParticleOptions(0xFF48C8)
    private val TITHE_HEAL_CORE = ConjureParticleOptions(0xFFF2FF)

    /** Breath Exchange: bright green threads first draw from the air, then sink into the mind. */
    private val BREATH = ConjureParticleOptions(0x55FF85)
    private val BREATH_CORE = ConjureParticleOptions(0xD9FFE4)

    /**
     * A full heart cannot keep love quiet. Four deliberately distinct colours make the reversed
     * current readable even in a crowded pen: affection, warning, spent light, and loss.
     */
    private val OVERFLOW = listOf(
        ConjureParticleOptions(0xFF5FB0) to ConjureParticleOptions(0xFFAEDC),
        ConjureParticleOptions(0xFFD84A) to ConjureParticleOptions(0xFFF1A3),
        ConjureParticleOptions(0xF8F4FF) to ConjureParticleOptions(0xFFFFFF),
        ConjureParticleOptions(0xFF3038) to ConjureParticleOptions(0xFF8A72),
    )

    /** How often the ambient motes are spawned, in ticks. Multiples of the bond ticker's interval. */
    const val LOVE_AMBIENT_INTERVAL = 20L
    const val JEALOUSY_AMBIENT_INTERVAL = 10L
    const val SIREN_CALL_INTERVAL = 60L

    /** The song is not re-announced when the called one is already at the caller's feet. */
    const val SIREN_QUIET_DISTANCE = 6.0

    /** Beyond this the beam would be more lag than spectacle; the ring alone is drawn. */
    private const val MAX_BEAM_LENGTH = 48.0

    /** Blocks of beam per tick. The arrow is meant to arrive almost at once. */
    private const val BEAM_SPEED = 4.0

    /** Spacing of the motes along the beam, and the ceiling on how many one tick may draw. */
    private const val BEAM_STEP = 0.5
    private const val BEAM_MAX_PER_TICK = 14

    private const val RING_POINTS = 8
    private const val MAX_ACTIVE = 64
    private const val MAX_PHEROMONE_CLOUDS = 12
    private const val MAX_TITHE_TENDRILS = 10
    private const val HEALTH_PER_TENDRIL = 2.0
    private const val BREATH_TENDRILS_PER_PLAYER = 4

    /** Nothing here may outlive this, whatever happens to the level it belongs to. */
    private const val WALL_CLOCK_LIMIT = 200

    private val active = ArrayList<ActiveFx>()
    private val pheromoneClouds = ArrayList<PheromoneCloud>()

    private interface ActiveFx {
        val level: ServerLevel
        var wall: Int

        fun advance(): Boolean
    }

    /**
     * Requirement 7: the charm, made visible. The arrow leaves [beloved] and lands on [charmed],
     * which is the direction the feeling actually travels in.
     */
    fun loveArrow(level: ServerLevel, beloved: Entity, charmed: Entity) =
        fire(level, beloved, charmed, LOVE, LOVE_PALE, ringTicks = 14, notes = false)

    /** Requirement 16: louder and angrier, since the bond it announces is neither long nor kind. */
    fun jealousyArrow(level: ServerLevel, guarded: Entity, jealous: Entity) =
        fire(level, guarded, jealous, JEALOUSY, JEALOUSY_PALE, ringTicks = 12, notes = false)

    /** Requirement 20: the song itself, repeated for as long as it holds. No ring: nothing was bound. */
    fun sirenCall(level: ServerLevel, caller: Entity, called: Entity) =
        fire(level, caller, called, SIREN, null, ringTicks = 0, notes = true)

    /** A small opening bloom; the long-lived part is [pheromoneCloud]. */
    fun pheromoneBurst(level: ServerLevel, centre: Vec3, radius: Double) {
        if (!enabled()) return
        val spread = radius.coerceAtLeast(0.5)
        val count = (6 + spread * 2.0).toInt().coerceIn(8, 38)
        level.sendParticles(PHEROMONE, centre.x, centre.y + 0.7, centre.z, count, spread * 0.45, 0.8, spread * 0.45, 0.018)
        level.sendParticles(PHEROMONE_CORE, centre.x, centre.y + 0.85, centre.z, (count * 0.55).toInt(), spread * 0.3, 0.55, spread * 0.3, 0.012)
    }

    /**
     * Keeps a slow, sparse perfume cloud alive for its whole spell duration. Minecraft particles do
     * not expose a server-side lifetime, so this deliberately releases a few new motes every tick;
     * their own natural fade overlaps with the next ones. The emission rate falls quadratically,
     * making the cloud visibly thin out towards the expiry rather than blink away.
     */
    fun pheromoneCloud(level: ServerLevel, centre: Vec3, radius: Double, beganAt: Long, expiresAt: Long) {
        if (!enabled() || expiresAt <= beganAt) return
        if (pheromoneClouds.size >= MAX_PHEROMONE_CLOUDS) pheromoneClouds.removeAt(0)
        pheromoneClouds += PheromoneCloud(level, centre, radius.coerceAtLeast(0.5), beganAt, expiresAt)
    }

    /** Three curling rays leave the caster and knot themselves into the newly summoned image. */
    fun phantomManifestation(level: ServerLevel, caster: Entity, phantom: Entity) {
        if (!enabled()) return
        val origin = mid(caster)
        val destination = mid(phantom)
        val distance = origin.distanceTo(destination)
        if (distance <= 1.0e-3) return

        level.sendParticles(PHANTOM, destination.x, destination.y, destination.z, 24, 0.45, 0.9, 0.45, 0.03)
        level.sendParticles(PHANTOM_CORE, destination.x, destination.y + 0.5, destination.z, 12, 0.22, 0.65, 0.22, 0.018)
        level.sendParticles(ParticleTypes.HEART, destination.x, destination.y + 0.75, destination.z, 5, 0.3, 0.45, 0.3, 0.01)

        val count = min(3, (MAX_ACTIVE - active.size).coerceAtLeast(0))
        repeat(count) { index ->
            val angle = 2.0 * PI * index / 3.0 + level.random.nextDouble() * 0.35
            val offset = Vec3(cos(angle) * 0.2, 0.15 + level.random.nextDouble() * 0.25, sin(angle) * 0.2)
            val curl = 0.7 + level.random.nextDouble() * 0.6
            val bend = Vec3(-sin(angle) * curl, 0.35 + level.random.nextDouble() * 0.6, cos(angle) * curl)
            val flightTicks = (ceil(distance / BEAM_SPEED).toInt() + 5).coerceIn(6, 18)
            active += CurvedRay(level, origin.add(offset), phantom, destination, PHANTOM, PHANTOM_CORE, flightTicks, bend)
        }
    }

    /** Blood-red motes leave the beloved and are swallowed by the caster's heart. */
    fun titheDrain(level: ServerLevel, source: Entity, caster: Entity) =
        fire(level, source, caster, TITHE, TITHE_PALE, ringTicks = 8, notes = false)

    /** Two parents' heart-motes curl into a nearby Amethyst Heart as their child is accepted by the world. */
    fun loveHarvest(level: ServerLevel, first: Entity, second: Entity, heart: BlockPos) {
        if (!enabled()) return
        val destination = Vec3.atCenterOf(heart).add(0.0, 0.7, 0.0)
        harvestRay(level, mid(first), destination, 0.0, LOVE, LOVE_PALE)
        harvestRay(level, mid(second), destination, PI, LOVE, LOVE_PALE)
        level.sendParticles(LOVE_PALE, destination.x, destination.y, destination.z, 8, 0.16, 0.22, 0.16, 0.018)
        level.sendParticles(ParticleTypes.HEART, destination.x, destination.y + 0.16, destination.z, 2, 0.12, 0.12, 0.12, 0.01)
    }

    /**
     * Exactly four coloured streams now run in the opposite direction, from the saturated heart
     * back to each parent. The multiplication and reversal are the player's unambiguous warning
     * that this breeding event could not be stored.
     */
    fun loveOverflow(level: ServerLevel, heart: BlockPos, first: Entity, second: Entity) {
        if (!enabled()) return
        val origin = Vec3.atCenterOf(heart).add(0.0, 0.7, 0.0)
        val parents = listOf(first, second)
        for ((parentIndex, parent) in parents.withIndex()) {
            val destination = mid(parent)
            OVERFLOW.forEachIndexed { colourIndex, (beam, tip) ->
                val phase = parentIndex * PI + colourIndex * PI / 2.0
                harvestRay(level, origin, destination, phase, beam, tip)
            }
        }
        level.sendParticles(OVERFLOW[0].first, origin.x, origin.y, origin.z, 10, 0.22, 0.28, 0.22, 0.025)
        level.sendParticles(OVERFLOW[1].first, origin.x, origin.y, origin.z, 10, 0.22, 0.28, 0.22, 0.025)
        level.sendParticles(OVERFLOW[2].first, origin.x, origin.y, origin.z, 10, 0.22, 0.28, 0.22, 0.025)
        level.sendParticles(OVERFLOW[3].first, origin.x, origin.y, origin.z, 10, 0.22, 0.28, 0.22, 0.025)
    }

    /**
     * The health that actually reached the recipient arrives as bright, curling tendrils from all
     * sides. Each two health points add another tendril, so a large recovery looks proportionally
     * more powerful without producing an unbounded packet burst.
     */
    fun titheHealing(level: ServerLevel, recipient: LivingEntity, healed: Float) {
        if (!enabled() || healed <= 0f) return
        val capacity = MAX_ACTIVE - active.size
        if (capacity <= 0) return

        val count = ceil(healed.toDouble() / HEALTH_PER_TENDRIL).toInt()
            .coerceIn(1, MAX_TITHE_TENDRILS)
            .coerceAtMost(capacity)
        val destination = mid(recipient)
        repeat(count) {
            val angle = level.random.nextDouble() * 2.0 * PI
            val radius = 1.8 + level.random.nextDouble() * 2.2
            val origin = destination.add(
                cos(angle) * radius,
                (level.random.nextDouble() - 0.5) * 2.4,
                sin(angle) * radius,
            )
            val curl = 0.35 + level.random.nextDouble() * 0.7
            val bend = Vec3(
                -sin(angle) * curl,
                (level.random.nextDouble() - 0.5) * 0.8,
                cos(angle) * curl,
            )
            active += CurvedRay(
                level = level,
                origin = origin,
                target = recipient,
                lastKnown = destination,
                beam = TITHE_HEAL,
                core = TITHE_HEAL_CORE,
                flightTicks = 8 + level.random.nextInt(5),
                bend = bend,
            )
        }
    }

    /** Each spouse pulls four green tendrils out into the air before they are absorbed into their head. */
    fun breathExchange(first: LivingEntity, second: LivingEntity) {
        (first.level() as? ServerLevel)?.let { breathTendrils(it, first) }
        if (second !== first) (second.level() as? ServerLevel)?.let { breathTendrils(it, second) }
    }

    /** Soulbind sends both the familiar core and several winding strands of the owner's soul into the ring. */
    fun soulbind(level: ServerLevel, owner: Entity, ring: Entity, colour: Int) {
        val beam = ConjureParticleOptions(colour)
        val pale = ConjureParticleOptions(lighten(colour))
        fire(
            level,
            owner,
            ring,
            beam,
            pale,
            ringTicks = 10,
            notes = false,
        )
        soulRays(level, owner, ring, beam, pale)
    }

    /** A single mote per second. A field of charmed animals has to stay a field of animals. */
    fun loveAmbient(level: ServerLevel, entity: Entity) = motes(level, entity, LOVE, 1, 0.03)

    /** A beloved held by the lover is close enough to make the ordinary glow bloom visibly. */
    fun carriedLoveAmbient(level: ServerLevel, entity: Entity) = motes(level, entity, LOVE, 5, 0.05)

    /** Three, because jealousy runs out on its own and is supposed to be alarming while it lasts. */
    fun jealousyAmbient(level: ServerLevel, entity: Entity) = motes(level, entity, JEALOUSY, 3, 0.04)

    /** Two light motes tell players which creature is currently serene without obscuring it. */
    fun pheromoneAmbient(level: ServerLevel, entity: Entity) {
        motes(level, entity, PHEROMONE, 1, 0.035)
        motes(level, entity, PHEROMONE_CORE, 1, 0.045)
    }

    /** The ideal breathes softly even while no new creature walks into its radius. */
    fun phantomAmbient(level: ServerLevel, entity: Entity) {
        motes(level, entity, PHANTOM, 2, 0.025)
        motes(level, entity, PHANTOM_CORE, 1, 0.035)
    }

    /** Advances the arrows in flight in [level]. Called once per server tick per level. */
    fun tick(level: ServerLevel) {
        tickPheromoneClouds(level)
        if (active.isEmpty()) return
        val it = active.iterator()
        while (it.hasNext()) {
            val flight = it.next()
            flight.wall++
            if (flight.wall > WALL_CLOCK_LIMIT) {
                it.remove()
                continue
            }
            if (flight.level !== level) continue
            if (!flight.advance()) it.remove()
        }
    }

    /** Dropped on shutdown so a fresh world never inherits somebody else's fireworks. */
    fun clear() {
        active.clear()
        pheromoneClouds.clear()
    }

    private fun tickPheromoneClouds(level: ServerLevel) {
        val it = pheromoneClouds.iterator()
        while (it.hasNext()) {
            val cloud = it.next()
            if (cloud.level !== level) continue
            if (level.gameTime >= cloud.expiresAt) {
                it.remove()
                continue
            }
            cloud.emit()
        }
    }

    private class PheromoneCloud(
        val level: ServerLevel,
        val centre: Vec3,
        val radius: Double,
        val beganAt: Long,
        val expiresAt: Long,
    ) {
        fun emit() {
            if (!enabled()) return
            val total = (expiresAt - beganAt).coerceAtLeast(1L)
            val remaining = (expiresAt - level.gameTime).coerceAtLeast(0L)
            val strength = (remaining.toDouble() / total).coerceIn(0.0, 1.0)
            // Five quiet motes per tick at the peak, approaching zero smoothly as the charm fades.
            val desired = 5.0 * strength * strength
            val count = desired.toInt() + if (level.random.nextDouble() < desired % 1.0) 1 else 0
            repeat(count) {
                val point = randomAirPoint()
                val driftX = (level.random.nextDouble() - 0.5) * 0.012
                val driftY = 0.002 + level.random.nextDouble() * 0.007
                val driftZ = (level.random.nextDouble() - 0.5) * 0.012
                val particle = if (level.random.nextBoolean()) PHEROMONE else PHEROMONE_CORE
                // count=0 preserves this tiny velocity instead of making a random burst at the point
                level.sendParticles(particle, point.x, point.y, point.z, 0, driftX, driftY, driftZ, 1.0)
            }
        }

        private fun randomAirPoint(): Vec3 {
            val distance = radius * Math.cbrt(level.random.nextDouble())
            val vertical = level.random.nextDouble() * 2.0 - 1.0
            val horizontal = distance * kotlin.math.sqrt(1.0 - vertical * vertical)
            val angle = level.random.nextDouble() * 2.0 * PI
            return centre.add(cos(angle) * horizontal, vertical * distance, sin(angle) * horizontal)
        }
    }

    private fun fire(
        level: ServerLevel,
        from: Entity,
        to: Entity,
        beam: ParticleOptions,
        ring: ParticleOptions?,
        ringTicks: Int,
        notes: Boolean,
    ) {
        if (!enabled()) return
        if (active.size >= MAX_ACTIVE) return

        val origin = mid(from)
        val distance = origin.distanceTo(mid(to))
        val flightTicks = if (distance > MAX_BEAM_LENGTH) 0
        else (ceil(distance / BEAM_SPEED).toInt()).coerceIn(2, 12)

        if (flightTicks == 0 && ringTicks == 0) return
        active += Flight(level, origin, to, mid(to), beam, ring, flightTicks, ringTicks, notes)
    }

    /** Three indirect rays make the soul connection feel like a ritual instead of a projectile. */
    private fun soulRays(
        level: ServerLevel,
        owner: Entity,
        ring: Entity,
        beam: ParticleOptions,
        core: ParticleOptions,
    ) {
        if (!enabled()) return
        val destination = mid(ring)
        val ownerCentre = mid(owner)
        if (ownerCentre.distanceTo(destination) > MAX_BEAM_LENGTH) return

        repeat(min(3, MAX_ACTIVE - active.size).coerceAtLeast(0)) {
            val angle = level.random.nextDouble() * 2.0 * PI
            val origin = ownerCentre.add(cos(angle) * 0.3, (level.random.nextDouble() - 0.5) * 0.5, sin(angle) * 0.3)
            val curl = 0.45 + level.random.nextDouble() * 0.75
            val bend = Vec3(
                -sin(angle) * curl,
                (level.random.nextDouble() - 0.5) * 1.2,
                cos(angle) * curl,
            )
            val flightTicks = (ceil(origin.distanceTo(destination) / BEAM_SPEED).toInt() + 3).coerceIn(5, 14)
            active += CurvedRay(level, origin, ring, destination, beam, core, flightTicks, bend)
        }
    }

    private fun harvestRay(
        level: ServerLevel,
        origin: Vec3,
        destination: Vec3,
        phase: Double,
        beam: ParticleOptions,
        tip: ParticleOptions,
    ) {
        val distance = origin.distanceTo(destination)
        if (distance > MAX_BEAM_LENGTH || distance < 1.0e-3) return
        val points = ceil(distance / BEAM_STEP).toInt().coerceIn(2, 28)
        for (index in 0..points) {
            val t = index.toDouble() / points
            val curve = sin(t * PI) * 0.18
            val at = origin.lerp(destination, t).add(cos(phase) * curve, sin(phase + t * PI) * curve, sin(phase) * curve)
            val options = if (index == points) tip else beam
            level.sendParticles(options, at.x, at.y, at.z, if (index == points) 3 else 1, 0.01, 0.01, 0.01, 0.0)
        }
    }

    private fun breathTendrils(level: ServerLevel, recipient: LivingEntity) {
        if (!enabled()) return
        val count = min(BREATH_TENDRILS_PER_PLAYER, (MAX_ACTIVE - active.size).coerceAtLeast(0))
        val head = head(recipient)
        repeat(count) {
            val angle = level.random.nextDouble() * 2.0 * PI
            val radius = 1.45 + level.random.nextDouble() * 1.45
            val outer = head.add(
                cos(angle) * radius,
                (level.random.nextDouble() - 0.35) * 1.65,
                sin(angle) * radius,
            )
            val curl = 0.24 + level.random.nextDouble() * 0.55
            val bend = Vec3(
                -sin(angle) * curl,
                (level.random.nextDouble() - 0.5) * 0.55,
                cos(angle) * curl,
            )
            active += BreathTendril(level, recipient, head, outer, bend)
        }
    }

    /** A few motes drifting up out of the creature. One packet each, so the counts stay tiny. */
    private fun motes(level: ServerLevel, entity: Entity, options: ParticleOptions, count: Int, rise: Double) {
        if (!enabled()) return
        val random = level.random
        val radius = max(0.25, entity.bbWidth * 0.4)
        repeat(count) {
            level.sendParticles(
                options,
                entity.x + (random.nextDouble() - 0.5) * 2.0 * radius,
                entity.y + entity.bbHeight * (0.35 + random.nextDouble() * 0.55),
                entity.z + (random.nextDouble() - 0.5) * 2.0 * radius,
                0, 0.0, rise, 0.0, 1.0,
            )
        }
    }

    private fun enabled() = HexloveServerConfig.config.toggles.bondParticles

    private fun lighten(colour: Int): Int {
        val r = (colour shr 16 and 0xff)
        val g = (colour shr 8 and 0xff)
        val b = (colour and 0xff)
        return ((r + (255 - r) / 2) shl 16) or ((g + (255 - g) / 2) shl 8) or (b + (255 - b) / 2)
    }

    private fun mid(entity: Entity): Vec3 = entity.position().add(0.0, entity.bbHeight * 0.6, 0.0)

    private fun head(entity: Entity): Vec3 = entity.position().add(0.0, entity.bbHeight * 0.82, 0.0)

    /**
     * One arrow of light plus the ring it ends in. The origin is frozen at the moment of the cast —
     * a beam whose tail keeps chasing a walking caster reads as a leash, not as a shot — while the
     * target is followed live, so the ring lands on the creature and not on where it used to stand.
     */
    private class Flight(
        override val level: ServerLevel,
        val origin: Vec3,
        private val target: Entity,
        private val lastKnown: Vec3,
        private val beam: ParticleOptions,
        private val ring: ParticleOptions?,
        private val flightTicks: Int,
        private val ringTicks: Int,
        private val notes: Boolean,
    ) : ActiveFx {
        private var age = 0
        override var wall = 0

        /** False once there is nothing left to draw. */
        override fun advance(): Boolean {
            val destination = if (target.isAlive && target.level() === level) mid(target) else lastKnown

            if (age < flightTicks) {
                drawBeam(destination, age.toDouble() / flightTicks, (age + 1).toDouble() / flightTicks)
                age++
                return true
            }

            val step = age - flightTicks
            if (ring == null || step >= ringTicks) return false
            drawRing(destination, step)
            age++
            return true
        }

        /** The stretch of the beam this tick is responsible for, as a fraction of the whole. */
        private fun drawBeam(destination: Vec3, from: Double, to: Double) {
            val length = origin.distanceTo(destination)
            if (length < 1.0e-3) return
            val points = min(ceil(length * (to - from) / BEAM_STEP).toInt(), BEAM_MAX_PER_TICK)
            if (points <= 0) return

            for (i in 0 until points) {
                val t = from + (to - from) * (i + 0.5) / points
                val at = origin.lerp(destination, t)
                level.sendParticles(beam, at.x, at.y, at.z, 1, 0.04, 0.04, 0.04, 0.0)
            }

            // the head of the arrow: a brighter knot of light, and for the song a note carried with it
            val head = origin.lerp(destination, to)
            level.sendParticles(beam, head.x, head.y, head.z, 3, 0.1, 0.1, 0.1, 0.0)
            if (notes && age % 2 == 0) {
                level.sendParticles(
                    ParticleTypes.NOTE,
                    head.x, head.y, head.z,
                    0, level.random.nextDouble(), 0.0, 0.0, 1.0,
                )
            }
        }

        /** A ring that climbs the creature from its feet to over its head while it turns. */
        private fun drawRing(centre: Vec3, step: Int) {
            val options = ring ?: return
            val progress = step.toDouble() / ringTicks
            val radius = max(0.55, target.bbWidth * 0.75) * (0.7 + 0.4 * progress)
            val y = target.y + target.bbHeight * (0.1 + progress * 1.05)
            val spin = progress * PI

            for (i in 0 until RING_POINTS) {
                val angle = spin + 2.0 * PI * i / RING_POINTS
                level.sendParticles(
                    options,
                    centre.x + cos(angle) * radius,
                    y,
                    centre.z + sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0,
                )
            }
        }
    }

    /** A moving particle ray whose middle bows sideways, so it reads as living soul-energy. */
    private class CurvedRay(
        override val level: ServerLevel,
        private val origin: Vec3,
        private val target: Entity,
        private val lastKnown: Vec3,
        private val beam: ParticleOptions,
        private val core: ParticleOptions,
        private val flightTicks: Int,
        private val bend: Vec3,
    ) : ActiveFx {
        private var age = 0
        override var wall = 0

        override fun advance(): Boolean {
            if (age >= flightTicks) return false
            val destination = if (target.isAlive && target.level() === level) mid(target) else lastKnown
            drawCurve(destination, age.toDouble() / flightTicks, (age + 1).toDouble() / flightTicks)
            age++
            return age < flightTicks
        }

        private fun drawCurve(destination: Vec3, from: Double, to: Double) {
            val distance = origin.distanceTo(destination)
            if (distance < 1.0e-3) return
            val points = min(ceil(distance * (to - from) / BEAM_STEP).toInt().coerceAtLeast(2), BEAM_MAX_PER_TICK)
            for (index in 0 until points) {
                val progress = from + (to - from) * (index + 0.5) / points
                val at = curvePoint(destination, progress)
                level.sendParticles(beam, at.x, at.y, at.z, 1, 0.025, 0.025, 0.025, 0.0)
            }
            val head = curvePoint(destination, to)
            level.sendParticles(core, head.x, head.y, head.z, 2, 0.045, 0.045, 0.045, 0.0)
        }

        private fun curvePoint(destination: Vec3, progress: Double): Vec3 =
            origin.lerp(destination, progress).add(bend.scale(4.0 * progress * (1.0 - progress)))
    }

    /** A tendril extends into empty air, then reverses and vanishes into the live target's head. */
    private class BreathTendril(
        override val level: ServerLevel,
        private val target: LivingEntity,
        private val lastKnownHead: Vec3,
        private val outer: Vec3,
        private val bend: Vec3,
    ) : ActiveFx {
        private var age = 0
        override var wall = 0

        override fun advance(): Boolean {
            val targetHead = if (target.isAlive && target.level() === level) head(target) else lastKnownHead
            if (age < OUTBOUND_TICKS) {
                drawCurve(lastKnownHead, outer, age.toDouble() / OUTBOUND_TICKS, (age + 1).toDouble() / OUTBOUND_TICKS, false)
            } else {
                val step = age - OUTBOUND_TICKS
                drawCurve(outer, targetHead, step.toDouble() / INBOUND_TICKS, (step + 1).toDouble() / INBOUND_TICKS, true)
            }
            age++
            return age < OUTBOUND_TICKS + INBOUND_TICKS
        }

        private fun drawCurve(from: Vec3, to: Vec3, start: Double, end: Double, enteringHead: Boolean) {
            val distance = from.distanceTo(to)
            if (distance < 1.0e-3) return
            val points = min(ceil(distance * (end - start) / BEAM_STEP).toInt().coerceAtLeast(2), BEAM_MAX_PER_TICK)
            for (index in 0 until points) {
                val progress = start + (end - start) * (index + 0.5) / points
                val at = curvePoint(from, to, progress)
                level.sendParticles(BREATH, at.x, at.y, at.z, 1, 0.025, 0.025, 0.025, 0.0)
            }
            if (enteringHead) {
                val head = curvePoint(from, to, end)
                level.sendParticles(BREATH_CORE, head.x, head.y, head.z, 2, 0.04, 0.04, 0.04, 0.0)
            }
        }

        private fun curvePoint(from: Vec3, to: Vec3, progress: Double): Vec3 =
            from.lerp(to, progress).add(bend.scale(4.0 * progress * (1.0 - progress)))

        private companion object {
            const val OUTBOUND_TICKS = 5
            const val INBOUND_TICKS = 8
        }
    }
}
