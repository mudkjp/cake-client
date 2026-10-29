import me.modmuss50.mpp.ReleaseType
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    id("dev.architectury.loom") version "1.17.491"
    id("me.modmuss50.mod-publish-plugin") version "2.0.0"
}

val loaderName = loom.platform.get().name.lowercase()
val obfuscated = !(findProperty("fabric.loom.disableObfuscation")?.toString()?.toBoolean() ?: false)
val javaVersion = (property("mod.java_version") as String).toInt()
val minecraftDependency = property("mod.minecraft_dependency") as String
val fabricLoaderVersion = property("deps.fabric_loader") as String

base {
    archivesName = "cake-client"
}

group = "dev.mudkip"
version = "${property("mod.version")}+${stonecutter.current.version}-$loaderName"

stonecutter {
    constants {
        match(loaderName, "fabric")
    }
}

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/")

    maven {
        name = "VendoredLibraries"
        url = uri(rootProject.file("libs/maven"))
    }

    exclusiveContent {
        forRepository {
            maven("https://api.modrinth.com/maven")
        }

        filter {
            includeGroup("maven.modrinth")
        }
    }
}

val modConfiguration = if (obfuscated) "modImplementation" else "implementation"
val embeddedLibraries = configurations.create("embeddedLibraries")

dependencies {
    minecraft("com.mojang:minecraft:${stonecutter.current.version}")

    if (obfuscated) {
        "mappings"(loom.officialMojangMappings())
    }

    add(modConfiguration, "net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    add(modConfiguration, "net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    add(modConfiguration, "com.terraformersmc:modmenu:${property("deps.modmenu")}")

    implementation("io.wispforest:owo-lib:0.13.1+26.3")
    include("io.wispforest:owo-lib:0.13.1+26.3")

    implementation("dev.mudkip:compresso-core:1.1.0")
    implementation("com.github.usefulness:webp-imageio:0.11.0")
    embeddedLibraries("dev.mudkip:compresso-core:1.1.0")
    embeddedLibraries("com.github.usefulness:webp-imageio:0.11.0")

    compileOnly("org.jspecify:jspecify:1.0.0")
}

loom {
    runConfigs.configureEach {
        ideConfigGenerated(true)
        environmentVars.put("GDK_BACKEND", "wayland")
    }
}

tasks.withType<JavaExec>().configureEach {
    doFirst {
        workingDir.mkdirs()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    withSourcesJar()
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version,
        "loader_version" to fabricLoaderVersion,
        "jdk_version" to javaVersion,
        "minecraft_dependency" to minecraftDependency
    )

    inputs.properties(properties)

    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

tasks.jar {
    inputs.files(embeddedLibraries)

    from(provider {
        embeddedLibraries.map { zipTree(it) }
    }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        rename("LICENSE", "LICENSE_webp-imageio")
        rename("NOTICE", "NOTICE_webp-imageio")
    }

    from(rootProject.file("LICENSE")) {
        rename { "${it}_cake" }
    }
}

val publishJar = if (obfuscated) tasks.named<AbstractArchiveTask>("remapJar") else tasks.named<AbstractArchiveTask>("jar")
val publishSourcesJar = if (obfuscated) tasks.named<AbstractArchiveTask>("remapSourcesJar") else tasks.named<AbstractArchiveTask>("sourcesJar")

publishMods {
    file = publishJar.flatMap { it.archiveFile }
    additionalFiles.from(publishSourcesJar.flatMap { it.archiveFile })
    displayName = "Cake Client ${project.version}"
    version = project.version.toString()
    type = providers.environmentVariable("RELEASE_TYPE").map { ReleaseType.valueOf(it) }.orElse(ReleaseType.STABLE)
    changelog = providers.environmentVariable("CHANGELOG").orElse("No changelog provided.")
    modLoaders.add(loaderName)

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "cake-client"
        minecraftVersions.add(stonecutter.current.version)
        requires("fabric-api")
        optional("modmenu")
        embeds("owo-lib")
    }
}
