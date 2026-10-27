package com.espflasher.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Every color the UI uses. Two instances: [LightPalette] and [DarkPalette]. */
data class Palette(
    val dark: Boolean,
    val brand: Color,        // sky blue — app-bar block, flashing
    val brandDeep: Color,    // header band, primary buttons
    val brandDeeper: Color,  // band gradient end
    val onBrand: Color,
    val accent: Color,
    val onAccent: Color,
    val canvasBackground: Color,
    val panelBackground: Color,
    val panelBackgroundRaised: Color,
    val panelBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textFaint: Color,
    val logBackground: Color,
    val logHeader: Color,
    val logText: Color,
    val logTime: Color,
    val logOk: Color,
    val logError: Color,
    val connected: Color,    // board present, running firmware
    val flashing: Color,
    val danger: Color,
    val success: Color,
    val warn: Color,
)

// nRF Connect–style light palette: Nordic blues over cool grey/white, dark slate log.
val LightPalette = Palette(
    dark = false,
    brand = Color(0xFF00A9CE),
    brandDeep = Color(0xFF0077C8),
    brandDeeper = Color(0xFF005A9C),
    onBrand = Color.White,
    accent = Color(0xFF0077C8),
    onAccent = Color.White,
    canvasBackground = Color(0xFFE8EDF1),
    panelBackground = Color.White,
    panelBackgroundRaised = Color(0xFFF4F6F8),
    panelBorder = Color(0xFFD3DCE3),
    textPrimary = Color(0xFF1F2D36),
    textSecondary = Color(0xFF546E7A),
    textFaint = Color(0xFF90A4AE),
    logBackground = Color(0xFF263238),
    logHeader = Color(0xFF37474F),
    logText = Color(0xFFCFD8DC),
    logTime = Color(0xFF78909C),
    logOk = Color(0xFF81C784),
    logError = Color(0xFFEF9A9A),
    connected = Color(0xFF3F6F96),
    flashing = Color(0xFF00A9CE),
    danger = Color(0xFFD32F2F),
    success = Color(0xFF2E9E5B),
    warn = Color(0xFFE08A00),
)

// Same family after dark: navy band, blue-slate panels, status colors lifted for contrast.
val DarkPalette = Palette(
    dark = true,
    brand = Color(0xFF00A9CE),
    brandDeep = Color(0xFF0B4F82),
    brandDeeper = Color(0xFF0A2E4D),
    onBrand = Color.White,
    accent = Color(0xFF2B8FD9),
    onAccent = Color.White,
    canvasBackground = Color(0xFF11161B),
    panelBackground = Color(0xFF192028),
    panelBackgroundRaised = Color(0xFF151B21),
    panelBorder = Color(0xFF2B3742),
    textPrimary = Color(0xFFE4EAEF),
    textSecondary = Color(0xFF9BABB8),
    textFaint = Color(0xFF66788A),
    logBackground = Color(0xFF0C1014),
    logHeader = Color(0xFF151C22),
    logText = Color(0xFFB8C4CE),
    logTime = Color(0xFF5F7181),
    logOk = Color(0xFF6FCF97),
    logError = Color(0xFFF28B82),
    connected = Color(0xFF6FA3D0),
    flashing = Color(0xFF22C3E6),
    danger = Color(0xFFEF5D5D),
    success = Color(0xFF4CC27E),
    warn = Color(0xFFF0A33A),
)

// Snapshot state, so every composable reading AppColors recomposes on a theme switch.
private var current by mutableStateOf(LightPalette)

/** The active palette. */
val AppColors: Palette get() = current

var darkTheme: Boolean
    get() = current.dark
    set(value) { current = if (value) DarkPalette else LightPalette }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val p = AppColors
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = Color(0xFF123A5C),
            onPrimaryContainer = Color(0xFFCBE3F7),
            secondary = p.textSecondary,
            background = p.canvasBackground,
            onBackground = p.textPrimary,
            surface = p.panelBackground,
            onSurface = p.textPrimary,
            surfaceVariant = p.panelBackgroundRaised,
            onSurfaceVariant = p.textSecondary,
            outline = p.panelBorder,
            outlineVariant = p.panelBorder,
            error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = Color(0xFFD6ECF8),
            onPrimaryContainer = p.brandDeeper,
            secondary = p.textSecondary,
            background = p.canvasBackground,
            onBackground = p.textPrimary,
            surface = p.panelBackground,
            onSurface = p.textPrimary,
            surfaceVariant = p.panelBackgroundRaised,
            onSurfaceVariant = p.textSecondary,
            outline = p.panelBorder,
            outlineVariant = p.panelBorder,
            error = p.danger,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
