package com.hypermusic.miuix.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.theme.*
import com.hypermusic.miuix.data.api.PlatformManager

val themeAccentColors = listOf(0L, 0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5, 0xFF2196F3, 0xFF00BCD4, 0xFF009688, 0xFF4FAF50, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFF795548, 0xFF607D8F, 0xFFFF9CA8)
val themePaletteStyles = listOf(ThemePaletteStyle.TonalSpot, ThemePaletteStyle.Neutral, ThemePaletteStyle.Vibrant, ThemePaletteStyle.Expressive, ThemePaletteStyle.Rainbow, ThemePaletteStyle.FruitSalad, ThemePaletteStyle.Monochrome, ThemePaletteStyle.Fidelity, ThemePaletteStyle.Content)

@Composable
fun CloudMusicMiuixTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (ThemeSettings.themeModeIndex) { 0 -> false; 1 -> true; else -> isSystemInDarkTheme() }
    val usesGeneratedColors = ThemeSettings.followMonet
    val followsSource = usesGeneratedColors && ThemeSettings.accentIndex == ThemeSettings.SOURCE_ACCENT_INDEX
    val sourceAccent = platformAccent(PlatformManager.current)
    val mode = if (usesGeneratedColors) {
        when (ThemeSettings.themeModeIndex) { 0 -> ColorSchemeMode.MonetLight; 1 -> ColorSchemeMode.MonetDark; else -> ColorSchemeMode.MonetSystem }
    } else {
        when (ThemeSettings.themeModeIndex) { 0 -> ColorSchemeMode.Light; 1 -> ColorSchemeMode.Dark; else -> ColorSchemeMode.System }
    }
    val key = when {
        followsSource -> sourceAccent.primary
        usesGeneratedColors && ThemeSettings.accentIndex > 0 -> Color(themeAccentColors[ThemeSettings.accentIndex])
        ThemeSettings.followMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
        usesGeneratedColors -> Color(0xFF6750A4)
        else -> null
    }
    val paletteStyle = themePaletteStyles[ThemeSettings.paletteStyleIndex]
    val colorSpec = if (ThemeSettings.colorSpecIndex == 1) ThemeColorSpec.Spec2025 else ThemeColorSpec.Spec2021
    val controller = ThemeController(mode, keyColor = key, isDark = dark,
        paletteStyle = paletteStyle, colorSpec = colorSpec)
    val density = LocalDensity.current
    MiuixTheme(controller = controller) {
        val themedContent: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * ThemeSettings.pageScale, density.fontScale),
            LocalEnableBlur provides (ThemeSettings.useFloatingBar || ThemeSettings.gaussianBlurEnabled),
            content = {
                top.yukonga.miuix.kmp.material.SoftGlassTheme(
                    enabled = ThemeSettings.controlStyleIndex == 0,
                    isDark = dark,
                    content = content
                )
            }
        )
        }
        if (followsSource && sourceAccent.secondary != null) {
            val secondary = ThemeController(mode, keyColor = sourceAccent.secondary, isDark = dark,
                paletteStyle = paletteStyle, colorSpec = colorSpec).currentColors()
            val tertiary = ThemeController(mode, keyColor = sourceAccent.tertiary ?: sourceAccent.secondary, isDark = dark,
                paletteStyle = paletteStyle, colorSpec = colorSpec).currentColors()
            val base = MiuixTheme.colorScheme
            val combined = remember(base, secondary, tertiary) {
                base.copy(
                    primaryVariant = secondary.primaryVariant,
                    onPrimaryVariant = secondary.onPrimaryVariant,
                    secondaryContainer = secondary.primaryContainer,
                    onSecondaryContainer = secondary.onPrimaryContainer,
                    tertiaryContainer = tertiary.primaryContainer,
                    onTertiaryContainer = tertiary.onPrimaryContainer,
                    tertiaryContainerVariant = tertiary.onPrimaryContainer
                )
            }
            MiuixTheme(colors = combined, content = themedContent)
        } else {
            themedContent()
        }
    }
}
