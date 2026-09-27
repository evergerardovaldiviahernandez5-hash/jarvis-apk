package com.jarvis.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class NovaPalette(
    val bg: Color,
    val bgElev: Color,
    val sidebar: Color,
    val sidebarHover: Color,
    val border: Color,
    val borderSoft: Color,
    val text: Color,
    val textSoft: Color,
    val muted: Color,
    val accent: Color,
    val bubble: Color,
    val codeBg: Color,
    val success: Color,
    val isDark: Boolean
)

private val LightPalette = NovaPalette(
    bg = Color(0xFFFAF9F5),
    bgElev = Color(0xFFFFFFFF),
    sidebar = Color(0xFFF0EEE6),
    sidebarHover = Color(0xFFE8E5DA),
    border = Color(0xFFE4E1D6),
    borderSoft = Color(0xFFEDEAE0),
    text = Color(0xFF1F1E1D),
    textSoft = Color(0xFF3D3C39),
    muted = Color(0xFF78776E),
    accent = Color(0xFFC96442),
    bubble = Color(0xFFF0EEE6),
    codeBg = Color(0xFFF6F5F0),
    success = Color(0xFF5FA85F),
    isDark = false
)

private val DarkPalette = NovaPalette(
    bg = Color(0xFF1F1E1D),
    bgElev = Color(0xFF262624),
    sidebar = Color(0xFF171716),
    sidebarHover = Color(0xFF242320),
    border = Color(0xFF35332F),
    borderSoft = Color(0xFF2C2A27),
    text = Color(0xFFF2F1EC),
    textSoft = Color(0xFFDAD8D1),
    muted = Color(0xFF96948B),
    accent = Color(0xFFD97757),
    bubble = Color(0xFF2E2C29),
    codeBg = Color(0xFF1A1917),
    success = Color(0xFF7CC77C),
    isDark = true
)

val LocalNovaPalette = staticCompositionLocalOf { LightPalette }

object NovaTheme {
    val palette: NovaPalette
        @Composable get() = LocalNovaPalette.current
}

@Composable
fun NovaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent,
            background = p.bg,
            surface = p.bgElev,
            onBackground = p.text,
            onSurface = p.text
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            background = p.bg,
            surface = p.bgElev,
            onBackground = p.text,
            onSurface = p.text
        )
    }
    CompositionLocalProvider(LocalNovaPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
