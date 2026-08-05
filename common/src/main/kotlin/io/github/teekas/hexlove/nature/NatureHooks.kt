package io.github.teekas.hexlove.nature

import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.config.HexloveServerConfig
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.food.FoodData
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Static facade called by every nature-related mixin.
 *
 * Rules, same as [io.github.teekas.hexlove.bond.BondGate]:
 * - Every public method is `@JvmStatic`.
 * - Every body is wrapped so that **nothing ever propagates a Throwable into vanilla code**.
 *
 * Extension points for stages 3 and 4 are marked with `// STAGE 3` / `// STAGE 4` comments
 * but contain no implementation yet.
 */
object NatureHooks {

    // ---- Diet interception (MixinNatureItemUse) ----

    /**
     * Called at the HEAD of `ItemStack.use`. Returns the verdict for [stack] under the
     * nature currently active on [player], or null if no nature is active.
     *
     * Null means: do nothing, let vanilla proceed.
     */
    @JvmStatic
    fun dietVerdict(player: Player, stack: ItemStack): DietVerdict? {
        return try {
            val nature = ChimeraNatures.of(player) ?: return null
            ChimeraDiet.verdict(nature, stack)
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("NatureHooks.dietVerdict failed", t)
            null
        }
    }

    // ---- Chew duration / animation / finish (MixinNatureChew) ----

    /**
     * Returns the chew duration for [item] if it is in any nature's chew table, else 0.
     * Only consulted when vanilla's own `getUseDuration` returned 0.
     *
     * This is context-free — it does not read player state — so it is safe to call from
     * `ItemStack.getUseDuration`, which has no player reference.
     */
    @JvmStatic
    fun chewDuration(item: Item): Int = try {
        // Use the larger of the two tables' values so that the duration is consistent
        // regardless of which nature will actually consume the item later.
        val sereneEntry = ChimeraDiet.entry(ChimeraNature.SERENE, item)
        val ferociousEntry = ChimeraDiet.entry(ChimeraNature.FEROCIOUS, item)
        maxOf(sereneEntry?.chewTicks ?: 0, ferociousEntry?.chewTicks ?: 0)
    } catch (t: Throwable) {
        Hexlove.LOGGER.error("NatureHooks.chewDuration failed", t)
        0
    }

    /**
     * Returns true when [item] is chewable by any nature. Used by [MixinNatureChew] to
     * decide whether to override `getUseAnimation` and `finishUsingItem`.
     */
    @JvmStatic
    fun isChewableByAnyNature(item: Item): Boolean = try {
        ChimeraDiet.isChewableByAnyNature(item)
    } catch (t: Throwable) {
        Hexlove.LOGGER.error("NatureHooks.isChewableByAnyNature failed", t)
        false
    }

    /**
     * Called at the HEAD of `ItemStack.finishUsingItem` for chewable items.
     *
     * Feeds [player] from the diet table, shrinks the stack by 1 (matching vanilla's own
     * `Item.finishUsingItem` contract), and returns the original stack. Returns null if the
     * entity is not a player or has no active nature, letting vanilla proceed instead.
     */
    @JvmStatic
    fun finishChewing(stack: ItemStack, level: Level, entity: LivingEntity): ItemStack? {
        return try {
            val player = entity as? Player ?: return null
            val nature = ChimeraNatures.of(player) ?: return null
            val entry = ChimeraDiet.entryOrFallback(nature, stack.item)

            if (!level.isClientSide) {
                player.foodData.eat(entry.nutrition, entry.saturation)
            }

            // Stage 3: record the meal time if a qualifying animal is nearby.
            recordMealNearAnimal(player, level)

            // Shrink exactly as vanilla Item.finishUsingItem does: in-place, not in creative.
            if (!player.abilities.instabuild) {
                stack.shrink(1)
            }
            stack
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("NatureHooks.finishChewing failed", t)
            null
        }
    }

    // ---- Eat bonus (MixinNaturePlayerEat) ----

    /**
     * Called at the TAIL of `Player.eat`. Tops the player up to the diet table's numbers if
     * vanilla gave less. Only ever adds, never subtracts.
     *
     * Also records meal memory for the humiliation trigger (STAGE 3).
     */
    @JvmStatic
    fun onPlayerEat(player: Player, stack: ItemStack, foodData: FoodData) {
        try {
            val nature = ChimeraNatures.of(player) ?: return
            val entry = ChimeraDiet.entry(nature, stack.item) ?: return

            // FoodProperties carries what vanilla just credited (before our boost).
            val vanilla = stack.item.foodProperties ?: return
            val deltaNutrition = entry.nutrition - vanilla.nutrition
            val deltaSaturation = entry.saturation - vanilla.saturationModifier

            // Only boost, never lower.
            if (deltaNutrition > 0 || deltaSaturation > 0f) {
                foodData.eat(
                    deltaNutrition.coerceAtLeast(0),
                    deltaSaturation.coerceAtLeast(0f),
                )
            }

            // Love-hearts around the eating player, just like a fed animal — this makes it
            // obvious that eating under a nature is "different" and is what lures nearby beasts.
            emitEatingHearts(player)

            // Stage 3: record the meal time if a qualifying animal is nearby.
            recordMealNearAnimal(player, player.level())
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("NatureHooks.onPlayerEat failed", t)
        }
    }

    // ---- Exhaustion multiplier (MixinNatureExhaustion) ----

    /**
     * Multiplies [exhaustion] by the nature's configured metabolic rate.
     * Returns the original value unchanged if no nature is active.
     */
    @JvmStatic
    fun multiplyExhaustion(player: Player, exhaustion: Float): Float = try {
        val cfg = HexloveServerConfig.config.nature
        when (ChimeraNatures.of(player)) {
            ChimeraNature.SERENE -> exhaustion * cfg.sereneExhaustionMultiplier.toFloat()
            ChimeraNature.FEROCIOUS -> exhaustion * cfg.ferociousExhaustionMultiplier.toFloat()
            null -> exhaustion
        }
    } catch (t: Throwable) {
        Hexlove.LOGGER.error("NatureHooks.multiplyExhaustion failed", t)
        exhaustion
    }

    // ---- Dig speed (MixinNatureDestroySpeed) ----

    /**
     * Returns the dig-speed multiplier for [player].
     * Serene nature: cfg.sereneDigSpeedMultiplier (default 0.8 = −20 %).
     * Any other state: 1.0 (no change).
     */
    @JvmStatic
    fun digSpeedMultiplier(player: Player): Float = try {
        if (ChimeraNatures.isSerene(player))
            HexloveServerConfig.config.nature.sereneDigSpeedMultiplier.toFloat()
        else 1.0f
    } catch (t: Throwable) {
        Hexlove.LOGGER.error("NatureHooks.digSpeedMultiplier failed", t)
        1.0f
    }

    // ---- Fleeing (MixinNatureAvoidGoal) ----

    /**
     * True when [mob] is an animal that should ignore [avoided] because it is a serene player.
     * Called from both `AvoidEntityGoal.canUse` and `canContinueToUse`, so it runs often and
     * deliberately does the cheapest checks first.
     */
    @JvmStatic
    fun calmsAvoidance(mob: LivingEntity?, avoided: LivingEntity?): Boolean = try {
        mob is Animal && avoided is Player && ChimeraNatures.isSerene(avoided)
    } catch (t: Throwable) {
        Hexlove.LOGGER.error("NatureHooks.calmsAvoidance failed", t)
        false
    }

    // ---- Safety net at the moment of actual eating (MixinNatureChew.finishUsingItem) ----

    /**
     * True when [entity] is a player with an active nature and [stack] is real food outside
     * that nature's diet. Called at the HEAD of `ItemStack.finishUsingItem` — if we return
     * true, the caller cancels the injection and returns the stack unchanged, so vanilla
     * never runs `Player.eat` and the stack never shrinks.
     *
     * This is the only piece of the diet gate that survives paths outside `ItemStack.use`
     * (Trinkets slot hotbar swaps, mods that invoke finishUsingItem directly, etc.).
     */
    @JvmStatic
    fun blocksFinishEating(entity: LivingEntity, stack: ItemStack): Boolean {
        return try {
            val player = entity as? Player ?: return false
            if (!stack.isEdible) false
            else dietVerdict(player, stack) == DietVerdict.FORBIDDEN
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("NatureHooks.blocksFinishEating failed", t)
            false
        }
    }

    // ---- Humiliation item-use block (MixinNatureItemUse) ----

    /**
     * Returns true when [player] is humiliated and [stack]'s registry id namespace is "hexcasting".
     * MixinNatureItemUse calls this before the diet verdict gate and cancels if true.
     *
     * Covers staff, cipher, trinket, artifact, spellbook, focus — anything Hex Casting registers.
     */
    @JvmStatic
    fun blocksHexItemForHumiliated(player: Player, stack: ItemStack): Boolean = try {
        if (!ChimeraNatures.isHumiliated(player)) false
        else {
            val key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.item)
            key.namespace == "hexcasting"
        }
    } catch (t: Throwable) {
        Hexlove.LOGGER.error("NatureHooks.blocksHexItemForHumiliated failed", t)
        false
    }

    // ---- Meal memory for humiliation trigger (MixinNaturePlayerEat) ----

    /**
     * Records the meal time in [MealMemory] when a serene player eats and at least one adult
     * Cow/Sheep/Pig is within 8 blocks.
     *
     * Server-side only. Called by MixinNaturePlayerEat after the eat bonus.
     */
    @JvmStatic
    fun recordMealNearAnimal(player: Player, level: Level) {
        try {
            if (level.isClientSide) return
            if (!ChimeraNatures.isSerene(player)) return
            val serverLevel = level as? ServerLevel ?: return

            val hasNearbyAnimal = serverLevel.getEntitiesOfClass(
                Animal::class.java,
                player.boundingBox.inflate(8.0),
            ) { it.isAlive && !it.isBaby && isHumiliationAnimal(it) }.isNotEmpty()

            if (hasNearbyAnimal) {
                MealMemory.record(player.uuid, serverLevel.gameTime)
            }
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("NatureHooks.recordMealNearAnimal failed", t)
        }
    }

    // ---- Advance trigger (MixinNatureAnimalAdvance) ----

    /**
     * Called at the TAIL of Animal.mobInteract. Starts the humiliation advance when all
     * conditions are met: result consumed an action, animal is adult Cow/Sheep/Pig, player is
     * serene, and the last meal was within advanceMealWindowTicks.
     *
     * MixinAnimalWorldAffection also injects into Animal.mobInteract at RETURN — both injectors
     * are independent and run in sequence without conflict.
     */
    @JvmStatic
    fun onAnimalAdvance(animal: Animal, player: Player, consumed: Boolean) {
        try {
            if (!consumed) return
            if (animal.isBaby) return
            if (!isHumiliationAnimal(animal)) return
            if (!ChimeraNatures.isSerene(player)) return
            if (HumiliationManager.isAdvancing(animal)) return

            val level = animal.level() as? ServerLevel ?: return
            val cfg = HexloveServerConfig.config.nature
            val lastMeal = MealMemory.get(player.uuid)
            if (lastMeal < 0L) return
            if (level.gameTime - lastMeal > cfg.advanceMealWindowTicks) return

            HumiliationManager.beginAdvance(animal, player)
        } catch (t: Throwable) {
            Hexlove.LOGGER.error("NatureHooks.onAnimalAdvance failed", t)
        }
    }

    /**
     * A puff of vanilla love-hearts around [player] when they eat under a nature — exactly what
     * a bred farm animal shows. Server-side only; called from onPlayerEat.
     */
    private fun emitEatingHearts(player: Player) {
        val level = player.level() as? ServerLevel ?: return
        level.sendParticles(
            net.minecraft.core.particles.ParticleTypes.HEART,
            player.x, player.y + player.bbHeight * 0.9, player.z,
            4, 0.35, 0.25, 0.35, 0.0,
        )
    }

    /** Returns true for adult Cow, Sheep, or Pig — the three animals that can trigger humiliation. */
    private fun isHumiliationAnimal(animal: Animal): Boolean =
        animal is net.minecraft.world.entity.animal.Cow ||
            animal is net.minecraft.world.entity.animal.Sheep ||
            animal is net.minecraft.world.entity.animal.Pig

    // ---- Villager hostility (MixinNatureVillagerSensor, MixinNatureVillagerTrade) ----

    /**
     * Called at RETURN of VillagerHostilesSensor.isHostile. Returns true when [entity] is a
     * ferocious player — causing the sensor to flag that player as the nearest hostile and
     * triggering villager panic AI. We do not touch villager brains directly.
     */
    @JvmStatic
    fun isFerociousHostile(entity: LivingEntity): Boolean = try {
        entity is Player && ChimeraNatures.isFerocious(entity)
    } catch (t: Throwable) {
        Hexlove.LOGGER.error("NatureHooks.isFerociousHostile failed", t)
        false
    }

    /**
     * Called at HEAD of Villager.mobInteract. Returns true when [player] is ferocious, which
     * causes the mixin to cancel the interaction with FAIL — trading is then impossible.
     */
    @JvmStatic
    fun blocksFerociousTrade(player: Player): Boolean = try {
        ChimeraNatures.isFerocious(player)
    } catch (t: Throwable) {
        Hexlove.LOGGER.error("NatureHooks.blocksFerociousTrade failed", t)
        false
    }
}
