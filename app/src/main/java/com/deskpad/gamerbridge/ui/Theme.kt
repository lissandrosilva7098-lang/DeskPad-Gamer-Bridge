package com.deskpad.gamerbridge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Red = Color(0xFFE53935)
private val RedDark = Color(0xFFFF6E66)

private val DarkColors = darkColorScheme(
    primary = RedDark,
    onPrimary = Color(0xFF3A0A08),
    primaryContainer = Color(0xFF5C1A16),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFC8C6CF),
    background = Color(0xFF121214),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF121214),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF26262B),
    onSurfaceVariant = Color(0xFFC9C5CE),
    surfaceContainer = Color(0xFF1C1C20),
    surfaceContainerHigh = Color(0xFF232328),
    outline = Color(0xFF8F8B95),
)

private val LightColors = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410003),
    background = Color(0xFFFCF8F8),
    surface = Color(0xFFFCF8F8),
    surfaceVariant = Color(0xFFEFE9EA),
    surfaceContainer = Color(0xFFF3EEEE),
    surfaceContainerHigh = Color(0xFFEDE7E8),
)

/** Cores de estado (sempre acompanhadas de texto, nunca só a cor). */
object StatusColors {
    val ok = Color(0xFF2E9E5B)
    val warn = Color(0xFFE0A100)
    val error = Color(0xFFE5484D)
    val neutral = Color(0xFF8F8B95)
}

@Composable
fun GamerBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
