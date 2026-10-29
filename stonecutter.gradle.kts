plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3-fabric"

tasks.register("buildAllVersions") {
    group = "build"
    description = "Builds the mod for every Minecraft version"
    dependsOn(stonecutter.tasks.named("build").map { it.values })
}

tasks.register("publishAllVersions") {
    group = "publishing"
    description = "Publishes the mod to Modrinth for every Minecraft version"
    dependsOn(stonecutter.tasks.named("publishMods").map { it.values })
}
