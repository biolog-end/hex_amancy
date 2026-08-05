package io.github.teekas.hexlove.client

import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** The only source of truth for client-side logic; fed by the sync packets. */
object ClientBondState {
    var loveTarget: UUID? = null
    var loveExpiresAt: Long = 0
    var lovePermanent: Boolean = false
    var jealousyTarget: UUID? = null
    var heartbroken: Boolean = false
    var married: Boolean = false
    var sirenCaller: UUID? = null
    /** A non-controlling flash from Pheromone Mist; counted down by the client tick. */
    var pheromonePulseTicks: Int = 0

    /** Empty when the beloved is unloaded or in another dimension (Requirement 9.11). */
    var targetPosition: Vec3? = null

    /** Requirement 16.7: distance from the guarded one to the nearest stranger, null if there is none. */
    var jealousyDistance: Double? = null

    var sirenPosition: Vec3? = null

    val hasLove: Boolean get() = loveTarget != null
    val hasJealousy: Boolean get() = jealousyTarget != null
    val hasSiren: Boolean get() = sirenCaller != null

    fun reset() {
        loveTarget = null
        loveExpiresAt = 0
        lovePermanent = false
        jealousyTarget = null
        heartbroken = false
        married = false
        sirenCaller = null
        pheromonePulseTicks = 0
        targetPosition = null
        jealousyDistance = null
        sirenPosition = null
    }
}

/** Pure overlay math, so the renderer stays a trivial wrapper. */
object OverlayMath {
    const val MIN_ALPHA = 0.08f
    const val MAX_ALPHA = 0.45f

    /**
     * Requirement 9.5: grows as the distance shrinks; Requirement 9.11: fixed minimum when the
     * target is unreachable. Always within [MIN_ALPHA, MAX_ALPHA].
     */
    fun loveOverlayAlpha(distance: Double?, pullDistance: Double): Float {
        if (distance == null) return MIN_ALPHA
        val span = max(pullDistance, 0.001)
        val closeness = 1.0 - min(distance / (span * 2.0), 1.0)
        return (MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * closeness).toFloat().coerceIn(MIN_ALPHA, MAX_ALPHA)
    }

    /**
     * Same quiet floor as love when nobody is near, but jealousy climbs far higher as somebody closes
     * in: at love's ceiling the warning read as a faint pink tinge.
     */
    const val JEALOUSY_MIN_ALPHA = MIN_ALPHA
    const val JEALOUSY_MAX_ALPHA = 0.8f

    /** Requirement 16.7: the closer a stranger gets to the protected one, the redder the world. */
    fun jealousyOverlayAlpha(distanceToNearest: Double?, guardRadius: Double): Float {
        if (distanceToNearest == null) return JEALOUSY_MIN_ALPHA
        val span = max(guardRadius, 0.001)
        val closeness = 1.0 - min(distanceToNearest / span, 1.0)
        return (JEALOUSY_MIN_ALPHA + (JEALOUSY_MAX_ALPHA - JEALOUSY_MIN_ALPHA) * closeness)
            .toFloat()
            .coerceIn(JEALOUSY_MIN_ALPHA, JEALOUSY_MAX_ALPHA)
    }

    /**
     * Requirement 9.8: a fraction of the angular difference, added to the player's own input, never
     * a hard set. Zero when the target is unreachable or within the pull distance.
     */
    fun cameraNudge(currentYaw: Float, desiredYaw: Float, factor: Float = 0.05f): Float {
        var delta = (desiredYaw - currentYaw) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta * factor
    }

    /** Yaw, in Minecraft's convention, that points from [from] at [to]. */
    fun yawTowards(from: Vec3, to: Vec3): Float {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return (Math.toDegrees(Math.atan2(dz, dx)).toFloat()) - 90f
    }

    /** Pitch, in Minecraft's convention, that points from [from] at [to]. */
    fun pitchTowards(from: Vec3, to: Vec3): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val horizontal = Math.sqrt(dx * dx + dz * dz)
        return (-Math.toDegrees(Math.atan2(dy, horizontal))).toFloat()
    }
}
