package com.espflasher

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.espflasher.flash.FlasherController
import com.espflasher.theme.AppTheme
import com.espflasher.ui.MainScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.swing.UIManager

/** Desktop entry point. Run with: gradlew :app:run */
fun main() {
    // Native look for the Swing folder picker instead of the default Metal theme.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    application {
        val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val controller = remember { FlasherController(scope) }
        Window(
            onCloseRequest = ::exitApplication,
            title = "ESPparaFlasher",
            state = rememberWindowState(width = 1240.dp, height = 760.dp),
        ) {
            AppTheme {
                MainScreen(controller)
            }
        }
    }
}
