package io.github.teekas.hexlove.worldsoul

import dev.architectury.event.events.common.LootEvent
import io.github.teekas.hexlove.registry.HexloveItems
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue

/** Adds the first crystallized affection to a modest, renewable-adventure-tier treasure source. */
object WorldSoulDungeonLoot {
    private const val DUNGEON_CRYSTAL_CHANCE = 0.2f

    fun init() {
        LootEvent.MODIFY_LOOT_TABLE.register { _, id, context, builtin ->
            if (builtin && id == BuiltInLootTables.SIMPLE_DUNGEON) {
                context.addPool(
                    LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0f))
                        .`when`(LootItemRandomChanceCondition.randomChance(DUNGEON_CRYSTAL_CHANCE))
                        .add(LootItem.lootTableItem(HexloveItems.CRYSTALLIZED_AFFECTION.value)),
                )
            }
        }
    }
}
