// Root build file: declares plugin versions once; the app module applies them.
plugins {
    kotlin("jvm") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
}
