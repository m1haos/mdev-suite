plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0" apply false
}

subprojects {
    apply(plugin = "java")

    val paperApi = rootProject.property("paperApi").toString()
    val apiVersion = rootProject.property("apiVersion").toString()

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencies {
        "compileOnly"("io.papermc.paper:paper-api:$paperApi")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
    }

    tasks.named<ProcessResources>("processResources") {
        val props = mapOf(
            "version" to project.version.toString(),
            "apiVersion" to apiVersion,
        )
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
