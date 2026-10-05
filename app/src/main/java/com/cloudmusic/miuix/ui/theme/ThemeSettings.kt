package com.cloudmusic.miuix.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ThemeSettings {
    private const val PREFS_NAME = "theme_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_USE_FLOATING_BAR = "use_floating_bar"
    private const val KEY_LYRIC_FONT_SIZE = "lyric_font_size"
    private const val KEY_LYRIC_TRANSLATION_RATIO = "lyric_translation_ratio"
    private const val KEY_USE_ROUND_COVER = "use_round_cover"
    private const val KEY_COVER_ROTATION = "cover_rotation"
    private const val KEY_COVER_STYLE = "cover_style"
    private const val KEY_ENABLE_DYNAMIC_COVER = "enable_dynamic_cover"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_LYRIC_CENTER = "lyric_center"
    private const val KEY_SHOW_CAST_BUTTON = "show_cast_button"

    // 0 = 浅色模式, 1 = 深色模式, 2 = 跟随系统
    var themeModeIndex by mutableIntStateOf(2)
        private set

    var useFloatingBar by mutableStateOf(false)
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

    // 封面样式: 0=卡片, 1=全屏渐变, 2=竖屏卡片，横屏全屏
    var coverStyleIndex by mutableIntStateOf(2)
        private set

    // 是否启用动态Live封面
    var enableDynamicCover by mutableStateOf(true)
        private set

    // 播放页保持屏幕常亮
    var keepScreenOn by mutableStateOf(true)
        private set

    // 保持歌词居中
    var lyricCenter by mutableStateOf(false)
        private set

    // 显示小米秒播快捷按钮（播放页投屏按钮）
    var showCastButton by mutableStateOf(true)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeModeIndex = prefs?.getInt(KEY_THEME_MODE, 2) ?: 2
        useFloatingBar = prefs?.getBoolean(KEY_USE_FLOATING_BAR, false) ?: false
        lyricFontSize = prefs?.getInt(KEY_LYRIC_FONT_SIZE, 28) ?: 28
        lyricTranslationRatio = prefs?.getInt(KEY_LYRIC_TRANSLATION_RATIO, 70) ?: 70
        useRoundCover = prefs?.getBoolean(KEY_USE_ROUND_COVER, false) ?: false
        coverRotationIndex = prefs?.getInt(KEY_COVER_ROTATION, 0) ?: 0
        coverStyleIndex = prefs?.getInt(KEY_COVER_STYLE, 2) ?: 2
        enableDynamicCover = prefs?.getBoolean(KEY_ENABLE_DYNAMIC_COVER, true) ?: true
        keepScreenOn = prefs?.getBoolean(KEY_KEEP_SCREEN_ON, true) ?: true
        lyricCenter = prefs?.getBoolean(KEY_LYRIC_CENTER, false) ?: false
        showCastButton = prefs?.getBoolean(KEY_SHOW_CAST_BUTTON, true) ?: true
    }

    fun updateThemeModeIndex(index: Int) {
        themeModeIndex = index
        prefs?.edit()?.putInt(KEY_THEME_MODE, index)?.apply()
    }

    fun updateUseFloatingBar(value: Boolean) {
        useFloatingBar = value
        prefs?.edit()?.putBoolean(KEY_USE_FLOATING_BAR, value)?.apply()
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

    fun updateCoverStyleIndex(index: Int) {
        coverStyleIndex = index
        prefs?.edit()?.putInt(KEY_COVER_STYLE, index)?.apply()
    }

    fun updateEnableDynamicCover(value: Boolean) {
        enableDynamicCover = value
        prefs?.edit()?.putBoolean(KEY_ENABLE_DYNAMIC_COVER, value)?.apply()
    }

    fun updateKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs?.edit()?.putBoolean(KEY_KEEP_SCREEN_ON, value)?.apply()
    }

    fun updateLyricCenter(value: Boolean) {
        lyricCenter = value
        prefs?.edit()?.putBoolean(KEY_LYRIC_CENTER, value)?.apply()
    }

    fun updateShowCastButton(value: Boolean) {
        showCastButton = value
        prefs?.edit()?.putBoolean(KEY_SHOW_CAST_BUTTON, value)?.apply()
    }
}
