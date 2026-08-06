package io.github.teekas.hexlove.item

import at.petrak.hexcasting.common.particles.ConjureParticleOptions
import at.petrak.hexcasting.common.lib.HexItems
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.entity.OpeningHeartDisplay
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.registry.HexloveSounds
import net.minecraft.ChatFormatting
import net.minecraft.sounds.SoundEvent
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A held-use loot crystal with deliberately poetic misses and server-authoritative rewards. */
class CrystallizedAffectionItem(properties: Properties) : Item(properties) {
    override fun getUseDuration(stack: ItemStack): Int = 32

    // The opening has its own two-handed ritual animation. In particular, this must never make the
    // player raise the crystal to their mouth like food.
    override fun getUseAnimation(stack: ItemStack): UseAnim = UseAnim.NONE

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        player.startUsingItem(hand)
        return InteractionResultHolder.consume(player.getItemInHand(hand))
    }

    override fun onUseTick(level: Level, entity: LivingEntity, stack: ItemStack, remainingUseDuration: Int) {
        val player = entity as? ServerPlayer ?: return
        val display = OpeningHeartDisplay.ensureFor(player, stack)
        display.moveFor(player, remainingUseDuration)
        ritualParticles(player, remainingUseDuration)

        val elapsed = getUseDuration(stack) - remainingUseDuration
        if (elapsed > 0 && elapsed % 8 == 0) {
            level.playSound(
                null,
                player.blockPosition(),
                HexloveSounds.CRYSTALLIZED_AFFECTION_STRAIN.value,
                SoundSource.PLAYERS,
                0.45f,
                0.92f + elapsed / 60.0f,
            )
        }
    }

    override fun releaseUsing(stack: ItemStack, level: Level, entity: LivingEntity, timeCharged: Int) {
        (entity as? ServerPlayer)?.let(OpeningHeartDisplay::removeFor)
    }

    override fun finishUsingItem(stack: ItemStack, level: Level, entity: LivingEntity): ItemStack {
        val player = entity as? ServerPlayer ?: return stack
        // The burst is voiced from burst(), where the tier is already known.
        open(player, level.random)
        OpeningHeartDisplay.removeFor(player)
        if (!player.abilities.instabuild) stack.shrink(1)
        return stack
    }

    private fun open(player: ServerPlayer, random: RandomSource) {
        // Legendary was 3%; the extra 3% comes from 1% of blue (was 21 → 20) and 2% of
        // purple (was 42 → 40). The other three tiers are unchanged.
        val tier = when (random.nextInt(100)) {
            in 0..3 -> RewardTier.SILENCE          // 4%
            in 4..33 -> RewardTier.SMALL_SPARK     // 30%
            in 34..53 -> RewardTier.WORLD_RESPONSE // 20%
            in 54..93 -> RewardTier.FORGOTTEN_ECHO // 40%
            else -> RewardTier.AUTHORS_LEGACY      // 6% (indices 94..99)
        }
        burst(player, tier)
        // The tier's own voice arrives on top of the shared unsealing.
        player.serverLevel().playSound(
            null,
            player.blockPosition(),
            tier.openSoundOf(),
            SoundSource.PLAYERS,
            tier.volume,
            1.0f,
        )
        when (tier) {
            RewardTier.SILENCE -> silence(player, random)
            RewardTier.SMALL_SPARK -> smallSpark(player, random)
            RewardTier.WORLD_RESPONSE -> worldResponse(player, random)
            RewardTier.FORGOTTEN_ECHO -> forgottenEcho(player, random)
            RewardTier.AUTHORS_LEGACY -> authorsLegacy(player, random)
        }
    }

    private fun ritualParticles(player: ServerPlayer, remainingUseDuration: Int) {
        val level = player.serverLevel()
        val progress = 1.0 - remainingUseDuration.toDouble() / getUseDuration(player.useItem)
        val centre = OpeningHeartDisplay.targetFor(player, progress)
        val look = player.lookAngle.normalize()
        var right = look.cross(Vec3(0.0, 1.0, 0.0))
        if (right.lengthSqr() < 0.001) right = Vec3(1.0, 0.0, 0.0)
        right = right.normalize()
        val up = right.cross(look).normalize()
        val radius = 0.72 - progress * 0.48
        val baseAngle = player.tickCount * 0.48

        repeat(6) { index ->
            val angle = baseAngle + index * PI / 3.0
            val ringOffset = right.scale(cos(angle) * radius).add(up.scale(sin(angle) * radius))
            val origin = centre.add(ringOffset)
            val inward = centre.subtract(origin).normalize()
            val mote = if ((index + player.tickCount) % 2 == 0) RITUAL_PINK else RITUAL_VIOLET
            level.sendParticles(mote, origin.x, origin.y, origin.z, 0, inward.x, inward.y, inward.z, 0.16)
        }
        level.sendParticles(RITUAL_PALE, centre.x, centre.y, centre.z, 2, 0.09, 0.09, 0.09, 0.01)
    }

    private fun burst(player: ServerPlayer, tier: RewardTier) {
        val level = player.serverLevel()
        val centre = OpeningHeartDisplay.targetFor(player, 1.0)
        val colour = ConjureParticleOptions(tier.colour)
        // The richer the tier, the deeper and larger the break sounds.
        level.playSound(
            null,
            player.blockPosition(),
            HexloveSounds.CRYSTALLIZED_AFFECTION_BURST.value,
            SoundSource.PLAYERS,
            tier.volume,
            tier.pitch,
        )
        level.sendParticles(
            colour,
            centre.x,
            centre.y,
            centre.z,
            tier.particleCount * 2,
            0.52,
            0.52,
            0.52,
            tier.speed,
        )
        level.sendParticles(
            ParticleTypes.END_ROD,
            centre.x,
            centre.y,
            centre.z,
            5 + tier.particleCount / 3,
            0.34,
            0.34,
            0.34,
            tier.speed * 0.45,
        )
    }

    private fun silence(player: ServerPlayer, random: RandomSource) {
        announceTier(player, "silence", SILENCE_MESSAGE_COUNT, ChatFormatting.GRAY)
        give(player, ItemStack(Items.AMETHYST_SHARD))
        repeat(2 + random.nextInt(3)) {
            give(player, ItemStack(VANILLA_FLOWERS[random.nextInt(VANILLA_FLOWERS.size)]))
        }
        ExperienceOrb.award(player.serverLevel(), player.position(), 4 + random.nextInt(6))
    }

    private fun smallSpark(player: ServerPlayer, random: RandomSource) {
        announceTier(player, "small_spark", SMALL_SPARK_MESSAGE_COUNT, ChatFormatting.GREEN)
        when (random.nextInt(3)) {
            0 -> {
                give(player, ItemStack(Items.AMETHYST_SHARD, 3 + random.nextInt(3)))
                when (random.nextInt(3)) {
                    0 -> effect(player, MobEffects.MOVEMENT_SPEED, 45 * 20)
                    1 -> effect(player, MobEffects.JUMP, 45 * 20)
                    else -> effect(player, MobEffects.REGENERATION, (10 + random.nextInt(6)) * 20)
                }
            }
            1 -> {
                repeat(2 + random.nextInt(3)) {
                    val flower = if (random.nextBoolean()) {
                        VANILLA_FLOWERS[random.nextInt(VANILLA_FLOWERS.size)]
                    } else {
                        CUSTOM_FLOWERS[random.nextInt(CUSTOM_FLOWERS.size)].value
                    }
                    give(player, ItemStack(flower))
                }
                give(player, ItemStack(Items.EXPERIENCE_BOTTLE, 2 + random.nextInt(3)))
            }
            else -> {
                give(player, ItemStack(HexItems.AMETHYST_DUST, 6))
                effect(
                    player,
                    if (random.nextBoolean()) MobEffects.DAMAGE_BOOST else MobEffects.MOVEMENT_SPEED,
                    60 * 20,
                )
            }
        }
    }

    private fun worldResponse(player: ServerPlayer, random: RandomSource) {
        announceTier(player, "world_response", WORLD_RESPONSE_MESSAGE_COUNT, ChatFormatting.BLUE)
        val first = weightedEffect(random, emptySet())
        val second = weightedEffect(random, setOf(first))
        effect(player, first, 3 * 60 * 20)
        effect(player, second, 3 * 60 * 20)
        give(player, ItemStack(HexItems.CHARGED_AMETHYST))
    }

    private fun forgottenEcho(player: ServerPlayer, random: RandomSource) {
        announceTier(player, "forgotten_echo", FORGOTTEN_ECHO_MESSAGE_COUNT, ChatFormatting.LIGHT_PURPLE)
        val params = LootParams.Builder(player.serverLevel())
            .withParameter(LootContextParams.ORIGIN, player.position())
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .create(LootContextParamSets.CHEST)
        val lootTable = player.server.lootData.getLootTable(Hexlove.id("gameplay/world_affection"))
        val rewardCount = 2 + random.nextInt(2)
        repeat(rewardCount) {
            var reward = ItemStack.EMPTY
            var attempts = 0
            while (reward.isEmpty && attempts < PURPLE_REWARD_REROLL_LIMIT) {
                val generated = lootTable.getRandomItems(params)
                    .filter { stack -> !stack.isEmpty && !isObviousJunk(stack) }
                if (generated.isNotEmpty()) {
                    reward = generated[random.nextInt(generated.size)]
                }
                attempts++
            }
            // A broken or externally replaced loot table must not hang the server or leave a reward slot empty.
            give(player, if (reward.isEmpty) ItemStack(Items.DIAMOND) else reward)
        }
    }

    private fun authorsLegacy(player: ServerPlayer, random: RandomSource) {
        val spell = LegendarySpellLibrary.random(random)
        player.sendSystemMessage(
            Component.translatable("item.hexlove.crystallized_affection.tier.authors_legacy")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
        )
        player.sendSystemMessage(LegendarySpellLibrary.createMessage(spell))
        give(player, LegendarySpellLibrary.createGrimoire(spell, player.displayName))
        give(player, LegendarySpellLibrary.createFocus(spell))
        effect(player, MobEffects.MOVEMENT_SPEED, 2 * 60 * 20, amplifier = 1)
        effect(player, MobEffects.INVISIBILITY, 2 * 60 * 20)
        HexloveAdvancements.grant(player, HexloveAdvancements.SECRETS_OF_THE_ANCIENTS)
    }

    private fun weightedEffect(random: RandomSource, excluded: Set<MobEffect>): MobEffect {
        val available = BLUE_EFFECTS.filterNot { it.first in excluded }
        val total = available.sumOf { it.second }
        var roll = random.nextDouble() * total
        for ((effect, weight) in available) {
            roll -= weight
            if (roll <= 0.0) return effect
        }
        return available.last().first
    }

    private fun effect(player: ServerPlayer, effect: MobEffect, ticks: Int, amplifier: Int = 0) {
        player.addEffect(MobEffectInstance(effect, ticks, amplifier))
    }

    private fun announceTier(player: ServerPlayer, tier: String, messageCount: Int, colour: ChatFormatting) {
        val index = player.random.nextInt(messageCount)
        val key = if (index == 0) {
            "item.hexlove.crystallized_affection.tier.$tier"
        } else {
            "item.hexlove.crystallized_affection.tier_message.$tier.$index"
        }
        player.sendSystemMessage(Component.translatable(key).withStyle(colour))
    }

    private fun give(player: ServerPlayer, stack: ItemStack) {
        if (!player.addItem(stack)) player.drop(stack, false)
    }

    private fun isObviousJunk(stack: ItemStack): Boolean =
        // A lore fragment on its own is a purple-tier tease with no payoff: the crystal
        // hands you an empty page. Filter it here so the reroll finds a real reward.
        stack.`is`(HexItems.LORE_FRAGMENT) ||
        stack.`is`(Items.ROTTEN_FLESH) ||
        stack.`is`(Items.BONE) ||
        stack.`is`(Items.STRING) ||
        stack.`is`(Items.SPIDER_EYE) ||
        stack.`is`(Items.POISONOUS_POTATO) ||
        stack.`is`(Items.BOWL) ||
        stack.`is`(Items.STICK) ||
        stack.`is`(Items.COAL) ||
        stack.`is`(Items.BUCKET) ||
        stack.`is`(Items.REDSTONE) ||
        stack.`is`(Items.WHEAT) ||
        stack.`is`(Items.BREAD) ||
        stack.`is`(Items.WHEAT_SEEDS) ||
        stack.`is`(Items.MELON_SEEDS) ||
        stack.`is`(Items.PUMPKIN_SEEDS) ||
        stack.`is`(Items.BEETROOT_SEEDS) ||
        stack.`is`(Items.TORCHFLOWER_SEEDS) ||
        stack.`is`(Items.PITCHER_POD) ||
        stack.`is`(Items.SAND) ||
        stack.`is`(Items.RED_SAND) ||
        stack.`is`(Items.SANDSTONE) ||
        stack.`is`(Items.RED_SANDSTONE) ||
        stack.`is`(Items.GUNPOWDER) ||
        stack.`is`(Items.RAIL) ||
        stack.`is`(Items.POWERED_RAIL) ||
        stack.`is`(Items.DETECTOR_RAIL) ||
        stack.`is`(Items.ACTIVATOR_RAIL) ||
        stack.`is`(Items.TORCH) ||
        stack.`is`(Items.SOUL_TORCH) ||
        stack.`is`(Items.REDSTONE_TORCH) ||
        stack.`is`(Items.HEART_OF_THE_SEA) ||
        stack.`is`(HexloveItems.CRYSTALLIZED_AFFECTION.value)

    private companion object {
        const val SILENCE_MESSAGE_COUNT = 9
        const val SMALL_SPARK_MESSAGE_COUNT = 10
        const val WORLD_RESPONSE_MESSAGE_COUNT = 11
        const val FORGOTTEN_ECHO_MESSAGE_COUNT = 32
        const val PURPLE_REWARD_REROLL_LIMIT = 32
        val RITUAL_PINK = ConjureParticleOptions(0xFF5FB0)
        val RITUAL_VIOLET = ConjureParticleOptions(0xB66CFF)
        val RITUAL_PALE = ConjureParticleOptions(0xFFE6FA)
        val VANILLA_FLOWERS = arrayOf(
            Items.DANDELION,
            Items.POPPY,
            Items.BLUE_ORCHID,
            Items.ALLIUM,
            Items.AZURE_BLUET,
            Items.RED_TULIP,
            Items.ORANGE_TULIP,
            Items.WHITE_TULIP,
            Items.PINK_TULIP,
            Items.OXEYE_DAISY,
            Items.CORNFLOWER,
            Items.LILY_OF_THE_VALLEY,
        )
        val CUSTOM_FLOWERS = arrayOf(
            HexloveItems.AMETHYST_ROSE,
            HexloveItems.AMETHYST_DANDELION,
            HexloveItems.AMETHYST_TULIP,
        )
        val BLUE_EFFECTS = listOf(
            MobEffects.MOVEMENT_SPEED to 1.0,
            MobEffects.DAMAGE_BOOST to 0.7,
            MobEffects.ABSORPTION to 0.25,
            MobEffects.REGENERATION to 0.25,
        )
    }

    private enum class RewardTier(
        val colour: Int,
        val particleCount: Int,
        val speed: Double,
        val volume: Float,
        val pitch: Float,
        val openSoundOf: () -> SoundEvent,
    ) {
        SILENCE(0xA8A8B7, 10, 0.08, 0.62f, 1.30f, { HexloveSounds.CRYSTALLIZED_AFFECTION_OPEN_SILENCE.value }),
        SMALL_SPARK(0x61E682, 18, 0.1, 0.75f, 1.14f, { HexloveSounds.CRYSTALLIZED_AFFECTION_OPEN_SPARK.value }),
        WORLD_RESPONSE(0x58AFFF, 28, 0.12, 0.85f, 1.00f, { HexloveSounds.CRYSTALLIZED_AFFECTION_OPEN_WORLD.value }),
        FORGOTTEN_ECHO(0xC77DFF, 42, 0.145, 0.95f, 0.88f, { HexloveSounds.CRYSTALLIZED_AFFECTION_OPEN_ECHO.value }),
        AUTHORS_LEGACY(0xFFD45A, 64, 0.18, 1.10f, 0.76f, { HexloveSounds.CRYSTALLIZED_AFFECTION_OPEN_LEGACY.value }),
    }
}
