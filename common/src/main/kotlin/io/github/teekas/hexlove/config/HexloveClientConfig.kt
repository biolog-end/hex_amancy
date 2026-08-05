package io.github.teekas.hexlove.config

import dev.architectury.event.events.client.ClientPlayerEvent
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.ConfigHolder
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry.Category
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.TransitiveObject
import me.shedaniel.autoconfig.serializer.PartitioningSerializer
import me.shedaniel.autoconfig.serializer.PartitioningSerializer.GlobalData
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer
import net.minecraft.world.InteractionResult
import io.github.teekas.hexlove.Hexlove

object HexloveClientConfig {
    @JvmStatic
    lateinit var holder: ConfigHolder<GlobalConfig>

    @JvmStatic
    val config get() = holder.config.client

    fun init() {
        holder = AutoConfig.register(
            GlobalConfig::class.java,
            PartitioningSerializer.wrap(::Toml4jConfigSerializer),
        )

        // when we change the server config in the client gui, also send it to the server config class
        holder.registerSaveListener { _, config ->
            HexloveServerConfig.holder.config = HexloveServerConfig.GlobalConfig(config.server)
            InteractionResult.PASS
        }

        // when we leave a server, clear our local copy of its config
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register { _ ->
            HexloveServerConfig.onSyncConfig(null)
        }
    }

    @Config(name = Hexlove.MODID)
    class GlobalConfig : GlobalData() {
        @Category("client")
        @TransitiveObject
        val client = ClientConfig()

        // this should only be used inside of this class; use HexloveServerConfig.config to access the server-side config in other code
        @Category("server")
        @TransitiveObject
        val server = HexloveServerConfig.ServerConfig()
    }

    @Config(name = "client")
    class ClientConfig : ConfigData {
        /** Requirement 6.4: pink vignette while charmed. */
        @Tooltip
        var loveOverlay: Boolean = true

        /** Requirement 6.4: red-pink vignette while jealous. */
        @Tooltip
        var jealousyOverlay: Boolean = true

        /** Requirement 6.5: on by default. */
        @Tooltip
        var smoothCameraNudge: Boolean = true
    }
}
