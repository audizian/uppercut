import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    alias(libs.plugins.pluginyml)
}

group = "dev.idot"
version = "0.2.1"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly(libs.spigot)
    //compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1+")
    compileOnly(libs.protocollib)
    compileOnly(libs.placeholderapi)
    implementation(libs.textcolor)
    implementation(libs.kotlin)
}

bukkit {
    main = "dev.idot.uppercut.Uppercut"
    name = rootProject.name
    description = project.description
    version = project.version.toString()
    author = "audizian"
    depend = listOf("ProtocolLib")
    softDepend = listOf("PlaceholderAPI")
    apiVersion = "1.13"
    BukkitPluginDescription.Command("uppercut").apply {
        description = "The main command for Uppercut"
    }.let(commands::add)
}

tasks {
    jar { enabled = false }
    named<ShadowJar>("shadowJar") {
        archiveClassifier = ""
    }
    build {
        dependsOn("shadowJar")
    }
}

kotlin {
    jvmToolchain(8)
}