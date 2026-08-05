package io.github.teekas.hexlove.entity

import com.mojang.math.Transformation
import io.github.teekas.hexlove.item.CrystallizedAffectionItem
import io.github.teekas.hexlove.mixin.MixinDisplayAccess
import io.github.teekas.hexlove.mixin.MixinItemDisplayAccess
import io.github.teekas.hexlove.registry.HexloveEntities
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

/**
 * A non-persistent visual copy of the crystal. It owns no loot and cannot be picked up; the real
 * stack remains in the player's inventory until the server finishes the opening ritual.
 */
class OpeningHeartDisplay(type: EntityType<*>, level: Level) : Display.ItemDisplay(type, level) {
    private var ownerId: UUID? = null

    fun bind(player: ServerPlayer, stack: ItemStack) {
        ownerId = player.uuid
        getSlot(0).set(stack.copyWithCount(1))
        (this as MixinDisplayAccess).hexloveSetBillboard(Display.BillboardConstraints.CENTER)
        (this as MixinItemDisplayAccess).hexloveSetItemTransform(ItemDisplayContext.FIXED)
        updateTransformation()
    }

    fun moveFor(player: ServerPlayer, remainingUseDuration: Int) {
        val duration = player.useItem.useDuration.coerceAtLeast(1)
        val progress = 1.0 - remainingUseDuration.toDouble() / duration
        val target = targetFor(player, progress)
        setPos(target.x, target.y, target.z)
        updateTransformation()
    }

    private fun updateTransformation() {
        val spin = ((tickCount * 0.15) % (PI * 2.0)).toFloat()
        val pulse = (0.62 + sin(tickCount * 0.22) * 0.035).toFloat()
        (this as MixinDisplayAccess).hexloveSetTransformation(
            Transformation(
                Vector3f(),
                Quaternionf().rotateZ(spin),
                Vector3f(pulse, pulse, pulse),
                Quaternionf(),
            ),
        )
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        val owner = ownerId?.let { (level() as ServerLevel).getPlayerByUUID(it) as? ServerPlayer }
        if (owner == null || !owner.isUsingItem || owner.useItem.item !is CrystallizedAffectionItem) {
            forgetAndDiscard()
            return
        }
        moveFor(owner, owner.useItemRemainingTicks)
    }

    // This is a momentary animation helper, never world state. A stopped server must not save a
    // half-open heart that could reappear as a ghost after the next start.
    override fun shouldBeSaved(): Boolean = false

    private fun forgetAndDiscard() {
        ownerId?.let { active.remove(it, uuid) }
        discard()
    }

    companion object {
        private val active = mutableMapOf<UUID, UUID>()

        fun ensureFor(player: ServerPlayer, stack: ItemStack): OpeningHeartDisplay {
            val level = player.serverLevel()
            val existing = active[player.uuid]
                ?.let(level::getEntity)
                as? OpeningHeartDisplay
            if (existing != null && !existing.isRemoved) return existing

            @Suppress("UNCHECKED_CAST")
            val type = HexloveEntities.OPENING_HEART.value as EntityType<OpeningHeartDisplay>
            return OpeningHeartDisplay(type, level).also { display ->
                display.bind(player, stack)
                display.moveFor(player, player.useItemRemainingTicks)
                level.addFreshEntity(display)
                active[player.uuid] = display.uuid
            }
        }

        fun removeFor(player: ServerPlayer) {
            val entityId = active.remove(player.uuid) ?: return
            (player.serverLevel().getEntity(entityId) as? OpeningHeartDisplay)?.discard()
        }

        fun targetFor(player: ServerPlayer, progress: Double): Vec3 {
            val eased = progress.coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }
            val distance = 0.46 + eased * 0.78
            val hover = sin((player.tickCount + progress) * 0.24) * 0.045
            return player.eyePosition
                .add(0.0, -0.38 + hover, 0.0)
                .add(player.lookAngle.normalize().scale(distance))
        }
    }
}
