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
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

    // 0 = 浅色模式, 1 = 深色模式, 2 = 跟随系统
    var themeModeIndex by mutableIntStateOf(2)
        private set

    var useFloatingBar by mutableStateOf(false)
        private set

    var lyricFontSize by mutableIntStateOf(26)
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

    // 播放页保持屏幕常亮
    var keepScreenOn by mutableStateOf(false)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeModeIndex = prefs?.getInt(KEY_THEME_MODE, 2) ?: 2
        useFloatingBar = prefs?.getBoolean(KEY_USE_FLOATING_BAR, false) ?: false
        lyricFontSize = prefs?.getInt(KEY_LYRIC_FONT_SIZE, 26) ?: 26
        lyricTranslationRatio = prefs?.getInt(KEY_LYRIC_TRANSLATION_RATIO, 70) ?: 70
        useRoundCover = prefs?.getBoolean(KEY_USE_ROUND_COVER, false) ?: false
        coverRotationIndex = prefs?.getInt(KEY_COVER_ROTATION, 0) ?: 0
        keepScreenOn = prefs?.getBoolean(KEY_KEEP_SCREEN_ON, false) ?: false
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

    fun updateKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs?.edit()?.putBoolean(KEY_KEEP_SCREEN_ON, value)?.apply()
    }
}
