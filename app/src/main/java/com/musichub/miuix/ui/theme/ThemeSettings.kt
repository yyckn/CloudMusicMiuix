package com.musichub.miuix.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ThemeSettings {
    private const val PREFS_NAME = "theme_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_USE_FLOATING_BAR = "use_floating_bar"
    private const val KEY_FOLLOW_MONET = "follow_monet"
    private const val KEY_LYRIC_FONT_SIZE = "lyric_font_size"
    private const val KEY_LYRIC_TRANSLATION_RATIO = "lyric_translation_ratio"
    private const val KEY_USE_ROUND_COVER = "use_round_cover"
    private const val KEY_COVER_ROTATION = "cover_rotation"
    private const val KEY_SMALL_SCREEN_COVER_STYLE = "small_screen_cover_style"
    private const val KEY_LARGE_SCREEN_COVER_STYLE = "large_screen_cover_style"
    private const val KEY_ENABLE_DYNAMIC_COVER = "enable_dynamic_cover"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_LYRIC_ALIGNMENT = "lyric_alignment"
    private const val KEY_SHOW_CAST_BUTTON = "show_cast_button"
    private const val KEY_PLAYER_COLOR_SCHEME = "player_color_scheme"
    private const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"

    // 0 = 浅色模式, 1 = 深色模式, 2 = 跟随系统
    var themeModeIndex by mutableIntStateOf(2)
        private set

    var useFloatingBar by mutableStateOf(false)
        private set

    // 跟随系统莫奈取色
    var followMonet by mutableStateOf(true)
        private set

    var lyricFontSize by mutableIntStateOf(28)
        private set

    // 翻译歌词倍率 (0.5-1.0)，默认0.7
    var lyricTranslationRatio by mutableIntStateOf(70) // 存储为百分比，70表示0.7
        private set

    // 是否使用圆形封面
    var useRoundCover by mutableStateOf(false)
        private set

    // 封面旋转模式: 0=静止, 1=播放时旋转, 2=始终旋转
    var coverRotationIndex by mutableIntStateOf(0)
        private set

    // 封面样式（小屏布局）: 0=卡片, 1=全屏渐变
    var smallScreenCoverStyle by mutableIntStateOf(0)
        private set

    // 封面样式（大屏布局）: 0=卡片, 1=全屏渐变
    var largeScreenCoverStyle by mutableIntStateOf(1)
        private set

    // 是否启用动态Live封面
    var enableDynamicCover by mutableStateOf(true)
        private set

    // 播放页保持屏幕常亮
    var keepScreenOn by mutableStateOf(true)
        private set

    // 歌词对齐方式: 0=靠左, 1=靠右
    var lyricAlignmentIndex by mutableIntStateOf(0)
        private set

    // 显示小米秒播快捷按钮（播放页投屏按钮）
    var showCastButton by mutableStateOf(true)
        private set

    // 播放页背景颜色: 0=跟随深浅色设置, 1=浅色流光, 2=深色流光
    var playerColorSchemeIndex by mutableIntStateOf(2)
        private set

    // 打开应用时自动检查更新
    var autoCheckUpdate by mutableStateOf(true)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeModeIndex = prefs?.getInt(KEY_THEME_MODE, 2) ?: 2
        useFloatingBar = prefs?.getBoolean(KEY_USE_FLOATING_BAR, false) ?: false
        followMonet = prefs?.getBoolean(KEY_FOLLOW_MONET, true) ?: true
        lyricFontSize = prefs?.getInt(KEY_LYRIC_FONT_SIZE, 28) ?: 28
        lyricTranslationRatio = prefs?.getInt(KEY_LYRIC_TRANSLATION_RATIO, 70) ?: 70
        useRoundCover = prefs?.getBoolean(KEY_USE_ROUND_COVER, false) ?: false
        coverRotationIndex = prefs?.getInt(KEY_COVER_ROTATION, 0) ?: 0
        smallScreenCoverStyle = prefs?.getInt(KEY_SMALL_SCREEN_COVER_STYLE, 0) ?: 0
        largeScreenCoverStyle = prefs?.getInt(KEY_LARGE_SCREEN_COVER_STYLE, 1) ?: 1
        enableDynamicCover = prefs?.getBoolean(KEY_ENABLE_DYNAMIC_COVER, true) ?: true
        keepScreenOn = prefs?.getBoolean(KEY_KEEP_SCREEN_ON, true) ?: true
        lyricAlignmentIndex = prefs?.getInt(KEY_LYRIC_ALIGNMENT, 0) ?: 0
        showCastButton = prefs?.getBoolean(KEY_SHOW_CAST_BUTTON, true) ?: true
        playerColorSchemeIndex = prefs?.getInt(KEY_PLAYER_COLOR_SCHEME, 2) ?: 2
        autoCheckUpdate = prefs?.getBoolean(KEY_AUTO_CHECK_UPDATE, true) ?: true
    }

    fun updateThemeModeIndex(index: Int) {
        themeModeIndex = index
        prefs?.edit()?.putInt(KEY_THEME_MODE, index)?.apply()
    }

    fun updateUseFloatingBar(value: Boolean) {
        useFloatingBar = value
        prefs?.edit()?.putBoolean(KEY_USE_FLOATING_BAR, value)?.apply()
    }

    fun updateFollowMonet(value: Boolean) {
        followMonet = value
        prefs?.edit()?.putBoolean(KEY_FOLLOW_MONET, value)?.apply()
    }

    fun updateLyricFontSize(size: Int) {
        lyricFontSize = size
        prefs?.edit()?.putInt(KEY_LYRIC_FONT_SIZE, size)?.apply()
    }

    fun updateLyricTranslationRatio(ratio: Int) {
        lyricTranslationRatio = ratio
        prefs?.edit()?.putInt(KEY_LYRIC_TRANSLATION_RATIO, ratio)?.apply()
    }

    fun updateUseRoundCover(value: Boolean) {
        useRoundCover = value
        prefs?.edit()?.putBoolean(KEY_USE_ROUND_COVER, value)?.apply()
    }

    fun updateCoverRotationIndex(index: Int) {
        coverRotationIndex = index
        prefs?.edit()?.putInt(KEY_COVER_ROTATION, index)?.apply()
    }

    fun updateSmallScreenCoverStyle(index: Int) {
        smallScreenCoverStyle = index
        prefs?.edit()?.putInt(KEY_SMALL_SCREEN_COVER_STYLE, index)?.apply()
    }

    fun updateLargeScreenCoverStyle(index: Int) {
        largeScreenCoverStyle = index
        prefs?.edit()?.putInt(KEY_LARGE_SCREEN_COVER_STYLE, index)?.apply()
    }

    fun updateEnableDynamicCover(value: Boolean) {
        enableDynamicCover = value
        prefs?.edit()?.putBoolean(KEY_ENABLE_DYNAMIC_COVER, value)?.apply()
    }

    fun updateKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs?.edit()?.putBoolean(KEY_KEEP_SCREEN_ON, value)?.apply()
    }

    fun updateLyricAlignment(index: Int) {
        lyricAlignmentIndex = index
        prefs?.edit()?.putInt(KEY_LYRIC_ALIGNMENT, index)?.apply()
    }

    fun updateShowCastButton(value: Boolean) {
        showCastButton = value
        prefs?.edit()?.putBoolean(KEY_SHOW_CAST_BUTTON, value)?.apply()
    }

    fun updatePlayerColorSchemeIndex(index: Int) {
        playerColorSchemeIndex = index
        prefs?.edit()?.putInt(KEY_PLAYER_COLOR_SCHEME, index)?.apply()
    }

    fun updateAutoCheckUpdate(value: Boolean) {
        autoCheckUpdate = value
        prefs?.edit()?.putBoolean(KEY_AUTO_CHECK_UPDATE, value)?.apply()
    }
}

/** 播放页是否使用深色：0=跟随深浅色设置, 1=浅色流光, 2=深色流光 */
@Composable
fun rememberPlayerDark(): Boolean {
    return when (ThemeSettings.playerColorSchemeIndex) {
        1 -> false
        2 -> true
        else -> when (ThemeSettings.themeModeIndex) {
            0 -> false
            1 -> true
            else -> isSystemInDarkTheme()
        }
    }
}
