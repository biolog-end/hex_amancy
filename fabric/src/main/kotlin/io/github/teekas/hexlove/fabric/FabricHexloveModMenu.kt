package io.github.teekas.hexlove.fabric

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import io.github.teekas.hexlove.HexloveClient

object FabricHexloveModMenu : ModMenuApi {
    override fun getModConfigScreenFactory() = ConfigScreenFactory(HexloveClient::getConfigScreen)
}
