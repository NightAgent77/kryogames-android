package com.kryogames.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class KryoAppearance { DARK, LIGHT }

/** Read during composition so a saved appearance repaints the shell. */
internal object KryoThemeState {
    var appearance by mutableStateOf(KryoAppearance.DARK)
}

internal object KryoColors {
    private fun pick(dark: Color, light: Color): Color =
        if (KryoThemeState.appearance == KryoAppearance.DARK) dark else light

    val Background get() = pick(Color(0xFF101417), Color(0xFFF3F5F8))
    val Letterbox get() = pick(Color(0xFF07090B), Color(0xFFD8DEE6))
    val Rail get() = pick(Color(0xFF0D1114), Color(0xFFFFFFFF))
    val Surface get() = pick(Color(0xFF1A1F24), Color(0xFFFFFFFF))
    val Border get() = pick(Color(0xFF303943), Color(0xFFD5DCE6))
    val Text get() = pick(Color(0xFFF4F5F7), Color(0xFF14181D))
    val Muted get() = pick(Color(0xFFB3BDCD), Color(0xFF5C6775))
    val Accent get() = pick(Color(0xFF43C5F1), Color(0xFF0B8FBE))
    val Green get() = pick(Color(0xFF62C655), Color(0xFF1F8A32))
    val Offline get() = pick(Color(0xFFE25B5B), Color(0xFFD14343))
    val Danger get() = pick(Color(0xFFF0A0A0), Color(0xFFC53636))
    val CardTop get() = pick(Color(0xFF1B2025), Color(0xFFFFFFFF))
    val CardBottom get() = pick(Color(0xFF14191D), Color(0xFFEEF2F6))
    val CardEdge get() = pick(Color(0xFF1F272D), Color(0xFFE1E6ED))
}

@Composable
fun KryoTheme(content: @Composable () -> Unit) {
    val dark = KryoThemeState.appearance == KryoAppearance.DARK
    val scheme = if (dark) {
        darkColorScheme(
            primary = KryoColors.Accent,
            background = KryoColors.Background,
            surface = KryoColors.Surface,
            onSurface = KryoColors.Text,
            onBackground = KryoColors.Text,
        )
    } else {
        lightColorScheme(
            primary = KryoColors.Accent,
            background = KryoColors.Background,
            surface = KryoColors.Surface,
            onSurface = KryoColors.Text,
            onBackground = KryoColors.Text,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
