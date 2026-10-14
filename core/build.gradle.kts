repositories {
    maven("https://jitpack.io")
    maven("https://repo.nexomc.com/releases")
    maven("https://repo.momirealms.net/releases")
}

dependencies {
    // Vault's own pom drags in an old Bukkit; Paper API already provides everything it needs.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
    // Custom item plugins. Each is read through one small class that loads only when the plugin is installed.
    compileOnly("com.nexomc:nexo:1.29.1") { isTransitive = false }
    compileOnly("com.github.LoneDev6:api-itemsadder:3.6.3-beta-14") { isTransitive = false }
    compileOnly("net.momirealms:craft-engine-bukkit:26.9.1") { isTransitive = false }
    compileOnly("net.momirealms:craft-engine-core:26.9.1") { isTransitive = false }
}

tasks.jar {
    archiveBaseName.set("MDevCore")
}
