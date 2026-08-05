@file:JvmName("HexloveAbstractions")

package io.github.teekas.hexlove

import dev.architectury.injectables.annotations.ExpectPlatform
import io.github.teekas.hexlove.registry.HexloveRegistrar
import net.minecraft.world.item.Item

fun initRegistries(vararg registries: HexloveRegistrar<*>) {
    for (registry in registries) {
        initRegistry(registry)
    }
}

@ExpectPlatform
fun <T : Any> initRegistry(registrar: HexloveRegistrar<T>) {
    throw AssertionError()
}

/** Forge needs a supplier-backed spawn egg because its item registry opens before entity types. */
@ExpectPlatform
fun createChimeraSpawnEgg(properties: Item.Properties): Item {
    throw AssertionError()
}

/** Adds the addon's items after Hex Casting has populated its own creative tab. */
@ExpectPlatform
fun registerHexCastingCreativeTabItems() {
    throw AssertionError()
}
