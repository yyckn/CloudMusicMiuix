package com.cloudmusic.miuix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

@Composable
fun CloudMusicMiuixTheme(
    content: @Composable () -> Unit
) {
    val colorSchemeMode = when (ThemeSettings.themeModeIndex) {
        0 -> ColorSchemeMode.Light
        1 -> ColorSchemeMode.Dark
        else -> ColorSchemeMode.System
    }

    val controller = ThemeController(colorSchemeMode)
    MiuixTheme(
        controller = controller,
        content = content
    )
}
