import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    // In-process serial access for probing/reboot (replaces spawning PowerShell/esptool per poll).
    implementation("com.fazecast:jSerialComm:2.11.0")
}

compose.desktop {
    application {
        mainClass = "com.espflasher.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "EspFlasher"
            packageVersion = "1.0.0"

            windows {
                shortcut = true
                menu = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "a3f1c8d2-4e7b-4a91-bc3d-5f9e2a7b1d04"
            }
        }
    }
}
