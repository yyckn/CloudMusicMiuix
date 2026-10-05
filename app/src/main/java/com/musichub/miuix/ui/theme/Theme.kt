package com.musichub.miuix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

@Composable
fun CloudMusicMiuixTheme(
    content: @Composable () -> Unit
) {
    val colorSchemeMode = if (ThemeSettings.followMonet) {
        // 跟随系统莫奈取色取色，同时遵循主题模式的深浅色选择
        when (ThemeSettings.themeModeIndex) {
            0 -> ColorSchemeMode.MonetLight
            1 -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetSystem
        }
    } else {
        when (ThemeSettings.themeModeIndex) {
            0 -> ColorSchemeMode.Light
            1 -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
    }

    val controller = ThemeController(colorSchemeMode)
    MiuixTheme(
        controller = controller,
        content = content
    )
}
