pluginManagement {
    repositories {
        // Repositories where you can get 
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        maven { url = uri("https://maven.blamejared.com/") }
    }
}

// Gradle forbids ':' in project names, so the display name lives in mods.toml/fabric.mod.json instead
rootProject.name = "hexlove"
include("common", "fabric", "forge")
