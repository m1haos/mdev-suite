import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("xyz.jpenilla.run-paper")
}

repositories {
    maven("https://repo.extendedclip.com/releases/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://jitpack.io")
    maven("https://repo.incredibleplugins.com/releases")
    maven("https://repo.glaremasters.me/repository/towny/")
    maven("https://mvn.lumine.io/repository/maven-public/")
}

dependencies {
    compileOnly(project(":core"))
    compileOnly("me.clip:placeholderapi:2.11.6") { isTransitive = false }
    // Claim plugins. The API of each is stable across versions, so these do not need to track releases.
    compileOnly("com.sk89q.worldguard:worldguard-core:7.0.9") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-core:7.2.15") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.15") { isTransitive = false }
    compileOnly("com.github.GriefPrevention:GriefPrevention:16.18.2") { isTransitive = false }
    compileOnly("com.incredibleplugins:lands-api:7.28.1") { isTransitive = false }
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0") { isTransitive = false }
    compileOnly("io.lumine:Mythic-Dist:5.12.1") { isTransitive = false }
}

tasks.jar {
    archiveBaseName.set("Eventide")
}

val coreJar = project(":core").tasks.named<Jar>("jar")

tasks.named<RunServer>("runServer") {
    minecraftVersion("26.2")
    pluginJars.from(coreJar)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    runDirectory.set(rootProject.layout.projectDirectory.dir("run/26.2"))
    jvmArgs("-Xms512M", "-Xmx1536M", "-Dcom.mojang.eula.agree=true")
}

tasks.register<RunServer>("runServerLegacy") {
    group = "run paper"
    description = "Runs a 1.21.8 test server, the oldest version the suite supports."
    minecraftVersion("1.21.8")
    pluginJars.from(tasks.jar, coreJar)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    runDirectory.set(rootProject.layout.projectDirectory.dir("run/1.21.8"))
    jvmArgs("-Xms512M", "-Xmx1536M", "-Dcom.mojang.eula.agree=true")
}
