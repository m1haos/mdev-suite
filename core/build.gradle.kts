repositories {
    maven("https://jitpack.io")
}

dependencies {
    // Vault's own pom drags in an old Bukkit; Paper API already provides everything it needs.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
}

tasks.jar {
    archiveBaseName.set("MDevCore")
}
