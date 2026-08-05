// A convention plugin that should be applied to all Minecraft-related subprojects, including common.

@file:Suppress("UnstableApiUsage")

package hexlove

import kotlin.io.path.div
import libs

plugins {
    id("hexlove.java")

    `maven-publish`
    id("dev.architectury.loom")
    id("at.petra-k.pkpcpbp.PKJson5Plugin")
}

val modId: String by project
val platform: String by project

base.archivesName = "${modId}-$platform"

loom {
    silentMojangMappingsLicense()
    accessWidenerPath = project(":common").file("src/main/resources/hexlove.accesswidener")

    mixin {
        // the default name includes both archivesName and the subproject, resulting in the platform showing up twice
        // default: hexlove-common-common-refmap.json
        // fixed:   hexlove-common.refmap.json
        defaultRefmapName = "${base.archivesName.get()}.refmap.json"

        // The legacy annotation processor writes a refmap into :common, but remapJar in the platform
        // projects drops the reference to it, so mixins targeting obfuscated methods die at runtime
        // with "No refMap loaded". Remapping the annotations themselves during remapJar has no such
        // gap: the published jar carries intermediary/SRG names directly.
        useLegacyMixinAp = false
    }
}

pkJson5 {
    autoProcessJson5 = true
    autoProcessJson5Flattening = true
}

dependencies {
    minecraft(libs.minecraft)

    mappings(loom.layered {
        officialMojangMappings()
        parchment(libs.parchment)
    })

    annotationProcessor(libs.bundles.asm)
}

sourceSets {
    main {
        kotlin {
            srcDir(file("src/main/java"))
        }
        resources {
            srcDir(file("src/generated/resources"))
        }
    }
}
