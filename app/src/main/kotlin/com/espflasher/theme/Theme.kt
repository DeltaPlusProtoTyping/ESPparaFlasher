package com.espflasher.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette borrowed from NodeNote's "Dark" pro-tool theme so the two apps feel related.
object AppColors {
    val accent = Color(0xFF5B9CFF)
    val onAccent = Color(0xFF0B1018)
    val canvasBackground = Color(0xFF15171B)
    val panelBackground = Color(0xFF17191D)
    val panelBackgroundRaised = Color(0xFF1E2126)
    val panelBorder = Color(0xFF2A2E35)
    val textPrimary = Color(0xFFE6E9EE)
    val textSecondary = Color(0xFF9AA3AF)
    val textFaint = Color(0xFF6B7280)
    val danger = Color(0xFFE2606B)
    val success = Color(0xFF53C27C)
    val warn = Color(0xFFE5B25B)
    val purple = Color(0xFFB07CE0)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = AppColors.accent,
        onPrimary = AppColors.onAccent,
        primaryContainer = Color(0xFF24364F),
        onPrimaryContainer = Color(0xFFCBDEFF),
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
