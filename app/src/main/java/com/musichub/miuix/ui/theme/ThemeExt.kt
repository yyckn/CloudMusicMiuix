package com.musichub.miuix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** 是否启用模糊/背景特效。 */
val LocalEnableBlur = staticCompositionLocalOf { true }

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean = when (ThemeSettings.themeModeIndex) {
    0 -> false
    1 -> true
    else -> isSystemInDarkTheme()
}
