pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.architectury.dev")
        maven("https://maven.kikugie.dev/releases")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) {
        fun mc(minecraftVersion: String, vararg loaders: String) {
            for (loader in loaders) {
                version("$minecraftVersion-$loader", minecraftVersion)
            }
        }

        mc("26.3", "fabric")
        vcsVersion = "26.3-fabric"
    }
}

rootProject.name = "cake-client"
