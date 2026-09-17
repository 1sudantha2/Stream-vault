package com.streamvault.player.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StreamVaultColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF9CF3FF),
    secondary = Color(0xFFB7A0FF),
    onSecondary = Color(0xFF27105C),
    secondaryContainer = Color(0xFF3B2775),
    onSecondaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF080B0F),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF161C26),
    onSurfaceVariant = Color(0xFFB4C0D0),
    outline = Color(0xFF334155),
    error = Color(0xFFFFB4AB)
)

@Composable
fun StreamVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreamVaultColors,
        content = content
    )
}

val Cyan = Color(0xFF00E5FF)
val Purple = Color(0xFF7C3AED)
val VaultSurface = Color(0xFF0D1117)
val VaultSurface2 = Color(0xFF161C26)
val VaultBorder = Color(0xFF1E2A3A)
val VaultMuted = Color(0xFF8A9AB0)
