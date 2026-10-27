package com.espflasher.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// nRF Connect–style light palette: Nordic blues over cool grey/white, dark slate log.
object AppColors {
    val brand = Color(0xFF00A9CE)        // sky blue — app-bar block, flashing
    val brandDeep = Color(0xFF0077C8)    // header band, primary buttons
    val brandDeeper = Color(0xFF005A9C)  // band gradient end
    val onBrand = Color.White

    val accent = brandDeep
    val onAccent = Color.White

    val canvasBackground = Color(0xFFE8EDF1)
    val panelBackground = Color.White
    val panelBackgroundRaised = Color(0xFFF4F6F8)
    val panelBorder = Color(0xFFD3DCE3)

    val textPrimary = Color(0xFF1F2D36)
    val textSecondary = Color(0xFF546E7A)
    val textFaint = Color(0xFF90A4AE)

    val logBackground = Color(0xFF263238)
    val logHeader = Color(0xFF37474F)
    val logText = Color(0xFFCFD8DC)
    val logTime = Color(0xFF78909C)
    val logOk = Color(0xFF81C784)
    val logError = Color(0xFFEF9A9A)

    val connected = Color(0xFF3F6F96)    // steel blue: board present, running firmware
    val flashing = brand
    val danger = Color(0xFFD32F2F)
    val success = Color(0xFF2E9E5B)
    val warn = Color(0xFFE08A00)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = AppColors.brandDeep,
        onPrimary = AppColors.onBrand,
        primaryContainer = Color(0xFFD6ECF8),
        onPrimaryContainer = AppColors.brandDeeper,
        secondary = AppColors.textSecondary,
        background = AppColors.canvasBackground,
        onBackground = AppColors.textPrimary,
        surface = AppColors.panelBackground,
        onSurface = AppColors.textPrimary,
        surfaceVariant = AppColors.panelBackgroundRaised,
        onSurfaceVariant = AppColors.textSecondary,
        outline = AppColors.panelBorder,
        outlineVariant = AppColors.panelBorder,
        error = AppColors.danger,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
