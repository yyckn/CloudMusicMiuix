package com.cloudmusic.miuix.ui.theme

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

@Composable
fun CloudMusicMiuixTheme(
    content: @Composable () -> Unit
) {
    val controller = ThemeController(ColorSchemeMode.System)
    MiuixTheme(
        controller = controller,
        content = content
    )
}
