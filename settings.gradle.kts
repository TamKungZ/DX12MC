pluginManagement {
    val loom_version: String by settings
    plugins {
        id("fabric-loom") version loom_version
    }
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "DX12MC"
