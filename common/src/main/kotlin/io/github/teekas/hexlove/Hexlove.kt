package io.github.teekas.hexlove

import net.minecraft.resources.ResourceLocation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import io.github.teekas.hexlove.config.HexloveServerConfig
import io.github.teekas.hexlove.marriage.MarriageAmbitExtension
import io.github.teekas.hexlove.nature.HumiliationCastBlock
import io.github.teekas.hexlove.networking.HexloveNetworking
import io.github.teekas.hexlove.registry.HexloveActions
import io.github.teekas.hexlove.registry.HexloveEntities
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.registry.HexloveBlocks
import io.github.teekas.hexlove.registry.HexloveBlockEntities
import io.github.teekas.hexlove.registry.HexloveMenus
import io.github.teekas.hexlove.registry.HexloveSounds
import io.github.teekas.hexlove.registry.HexloveEffects
import io.github.teekas.hexlove.worldsoul.WorldSoulDungeonLoot

object Hexlove {
    const val MODID = "hexlove"

    @JvmField
    val LOGGER: Logger = LogManager.getLogger(MODID)

    @JvmStatic
    fun id(path: String) = ResourceLocation(MODID, path)

    fun init() {
        HexloveServerConfig.init()
        initRegistries(
            HexloveActions,
            HexloveEntities,
            HexloveBlocks,
            HexloveItems,
            HexloveBlockEntities,
            HexloveMenus,
            HexloveSounds,
            HexloveEffects,
        )
        WorldSoulDungeonLoot.init()
        registerHexCastingCreativeTabItems()
        HexloveNetworking.init()
        HexloveEvents.init()
        MarriageAmbitExtension.init()
        HumiliationCastBlock.init()
    }

    /** Work that must run only after loader registries contain every mod's entries. */
    fun initPostRegistry() {
        HexloveActions.checkSignatureCollisions()
    }

    fun initServer() {
        HexloveServerConfig.initServer()
    }
}
