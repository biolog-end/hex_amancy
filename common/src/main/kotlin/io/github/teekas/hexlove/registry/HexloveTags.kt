package io.github.teekas.hexlove.registry

import io.github.teekas.hexlove.Hexlove
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item

object HexloveTags {
    /** Requirement 10.1/10.4: bosses and anything a datapack wants to protect. */
    val CHARM_IMMUNE: TagKey<EntityType<*>> =
        TagKey.create(Registries.ENTITY_TYPE, Hexlove.id("charm_immune"))

    /** Requirement 25.6: datapack escape hatch for animals that should not count as food-breedable. */
    val NOT_FOOD_BREEDABLE: TagKey<EntityType<*>> =
        TagKey.create(Registries.ENTITY_TYPE, Hexlove.id("not_food_breedable"))

    /** Everything a serene chimera may eat. Both stews are included so nature switching works. */
    val HERBIVORE_DIET: TagKey<Item> =
        TagKey.create(Registries.ITEM, Hexlove.id("herbivore_diet"))

    /** Everything a ferocious chimera may eat. Both stews are included so nature switching works. */
    val GHOUL_DIET: TagKey<Item> =
        TagKey.create(Registries.ITEM, Hexlove.id("ghoul_diet"))

    /** Helper tag used by the Stew of Serenity recipe — vanilla 1.20.1 has no seeds tag. */
    val SEEDS: TagKey<Item> =
        TagKey.create(Registries.ITEM, Hexlove.id("seeds"))
}
