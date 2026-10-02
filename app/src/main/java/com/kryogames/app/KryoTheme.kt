package com.kryogames.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal object KryoColors {
    val Background = Color(0xFF101417)
    val Letterbox = Color(0xFF07090B)
    val Rail = Color(0xFF0D1114)
    val Surface = Color(0xFF1A1F24)
    val Border = Color(0xFF303943)
    val Text = Color(0xFFF4F5F7)
    val Muted = Color(0xFFB3BDCD)
    val Accent = Color(0xFF43C5F1)
    val Green = Color(0xFF62C655)
    val Offline = Color(0xFFE25B5B)
}

@Composable
fun KryoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = KryoColors.Accent,
        background = KryoColors.Background,
        surface = KryoColors.Surface,
        onSurface = KryoColors.Text,
        onBackground = KryoColors.Text,
    ), content = content)
}
