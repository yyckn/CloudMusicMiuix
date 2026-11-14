package com.hypermusic.miuix.ui.theme

import androidx.compose.ui.graphics.Color
import com.hypermusic.miuix.data.api.MusicPlatform

internal data class PlatformAccent(val primary: Color, val secondary: Color? = null, val tertiary: Color? = null)

internal fun platformAccent(platform: MusicPlatform): PlatformAccent = when (platform) {
    MusicPlatform.NETEASE -> PlatformAccent(Color(0xFFFF0000))
    MusicPlatform.QQ -> PlatformAccent(Color(0xFF1ABF75))
    MusicPlatform.BODIAN -> PlatformAccent(Color(0xFF05E8B2))
    MusicPlatform.KUGOU -> PlatformAccent(Color(0xFF0062FF))
    MusicPlatform.QISHUI -> PlatformAccent(Color(0xFF24FEC3), Color(0xFF41FE43), Color(0xFFDDFC46))
}

internal fun accentMenuIndex(savedIndex: Int): Int = when (savedIndex) {
    ThemeSettings.SOURCE_ACCENT_INDEX -> 1
    0 -> 0
    else -> savedIndex + 1
}

internal fun accentSavedIndex(menuIndex: Int): Int = when (menuIndex) {
    1 -> ThemeSettings.SOURCE_ACCENT_INDEX
    0 -> 0
    else -> menuIndex - 1
}
