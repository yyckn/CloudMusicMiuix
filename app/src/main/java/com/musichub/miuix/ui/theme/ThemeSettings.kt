package com.musichub.miuix.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.musichub.miuix.R

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
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_WORD_BY_WORD_LYRICS = "word_by_word_lyrics"
    private const val KEY_SHOW_CAST_BUTTON = "show_cast_button"
    private const val KEY_SHOW_QUALITY = "show_quality"
    private const val KEY_PLAYER_COLOR_SCHEME = "player_color_scheme"
    private const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
    private const val KEY_FADE_ENABLED = "fade_enabled"
    private const val KEY_AUDIO_EFFECT_PRESET = "audio_effect_preset"
    private const val KEY_LYRIC_SOURCE = "lyric_source_preference"
    private const val KEY_AUTO_UPGRADE_LYRICS = "auto_upgrade_lyrics"
    private const val KEY_QQ_LYRICS_ENABLED = "qq_lyrics_enabled"
    private const val KEY_KUGOU_LYRICS_ENABLED = "kugou_lyrics_enabled"
    private const val KEY_SHOW_JAPANESE_ROMAJI = "show_japanese_romaji"
    private const val KEY_JAPANESE_NO_UPGRADE = "japanese_no_upgrade"

    // 0 = 浅色模式, 1 = 深色模式, 2 = 跟随系统
    var themeModeIndex by mutableIntStateOf(2)
        private set

    var useFloatingBar by mutableStateOf(false)
        private set

    // 跟随系统莫奈取色
    var followMonet by mutableStateOf(true)
        private set

    var lyricFontSize by mutableIntStateOf(32)
        private set

    // 翻译歌词倍率 (0.5-1.0)，默认0.6
    var lyricTranslationRatio by mutableIntStateOf(60) // 存储为百分比，60表示0.6
        private set

    // 逐字歌词：有 YRC 时显示逐字扫光，并同步给词幕
    var wordByWordLyrics by mutableStateOf(true)
        private set

    // 歌词来源偏好: 0=智能选择, 1=优先网易云音乐, 2=优先QQ音乐, 3=优先酷狗音乐
    var lyricSourcePreferenceIndex by mutableIntStateOf(0)
        private set

    // 当前歌词非逐字时，尝试从其他平台获取逐字歌词覆盖
    var autoUpgradeLyrics by mutableStateOf(true)
        private set

    // 各平台歌词开关（网易云音乐始终启用）
    var qqLyricsEnabled by mutableStateOf(true)
        private set

    var kugouLyricsEnabled by mutableStateOf(true)
        private set

    // 日语歌曲显示罗马音（由网易云提供）
    var showJapaneseRomaji by mutableStateOf(true)
        private set

    // 日语歌曲不升级为其他平台的逐字歌词（用于保留网易云罗马音）
    var japaneseNoUpgrade by mutableStateOf(false)
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

    // 播放页保持屏幕常亮
    var keepScreenOn by mutableStateOf(true)
        private set

    // 显示小米秒播快捷按钮（播放页投屏按钮）
    var showCastButton by mutableStateOf(true)
        private set

    // 显示当前歌曲音质
    var showQuality by mutableStateOf(true)
        private set

    // 播放页背景颜色: 0=跟随深浅色设置, 1=浅色流光, 2=深色流光
    var playerColorSchemeIndex by mutableIntStateOf(2)
        private set

    // 打开应用时自动检查更新
    var autoCheckUpdate by mutableStateOf(true)
        private set

    // 播放/暂停时音频渐入渐出
    var fadeEnabled by mutableStateOf(false)
        private set

    // 当前音效预设 id（默认音效为 "default"）
    var audioEffectPresetId by mutableStateOf("default")
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeModeIndex = prefs?.getInt(KEY_THEME_MODE, 2) ?: 2
        useFloatingBar = prefs?.getBoolean(KEY_USE_FLOATING_BAR, false) ?: false
        followMonet = prefs?.getBoolean(KEY_FOLLOW_MONET, true) ?: true
        lyricFontSize = prefs?.getInt(KEY_LYRIC_FONT_SIZE, 32) ?: 32
        lyricTranslationRatio = prefs?.getInt(KEY_LYRIC_TRANSLATION_RATIO, 60) ?: 60
        useRoundCover = prefs?.getBoolean(KEY_USE_ROUND_COVER, false) ?: false
        coverRotationIndex = prefs?.getInt(KEY_COVER_ROTATION, 0) ?: 0
        smallScreenCoverStyle = prefs?.getInt(KEY_SMALL_SCREEN_COVER_STYLE, 0) ?: 0
        largeScreenCoverStyle = prefs?.getInt(KEY_LARGE_SCREEN_COVER_STYLE, 1) ?: 1
        keepScreenOn = prefs?.getBoolean(KEY_KEEP_SCREEN_ON, true) ?: true
        wordByWordLyrics = prefs?.getBoolean(KEY_WORD_BY_WORD_LYRICS, true) ?: true
        lyricSourcePreferenceIndex = prefs?.getInt(KEY_LYRIC_SOURCE, 0) ?: 0
        autoUpgradeLyrics = prefs?.getBoolean(KEY_AUTO_UPGRADE_LYRICS, true) ?: true
        qqLyricsEnabled = prefs?.getBoolean(KEY_QQ_LYRICS_ENABLED, true) ?: true
        kugouLyricsEnabled = prefs?.getBoolean(KEY_KUGOU_LYRICS_ENABLED, true) ?: true
        showJapaneseRomaji = prefs?.getBoolean(KEY_SHOW_JAPANESE_ROMAJI, true) ?: true
        japaneseNoUpgrade = prefs?.getBoolean(KEY_JAPANESE_NO_UPGRADE, false) ?: false
        showCastButton = prefs?.getBoolean(KEY_SHOW_CAST_BUTTON, true) ?: true
        showQuality = prefs?.getBoolean(KEY_SHOW_QUALITY, true) ?: true
        playerColorSchemeIndex = prefs?.getInt(KEY_PLAYER_COLOR_SCHEME, 2) ?: 2
        autoCheckUpdate = prefs?.getBoolean(KEY_AUTO_CHECK_UPDATE, true) ?: true
        fadeEnabled = prefs?.getBoolean(KEY_FADE_ENABLED, false) ?: false
        audioEffectPresetId = prefs?.getString(KEY_AUDIO_EFFECT_PRESET, "default") ?: "default"
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

    fun updateKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs?.edit()?.putBoolean(KEY_KEEP_SCREEN_ON, value)?.apply()
    }

    fun updateWordByWordLyrics(value: Boolean) {
        wordByWordLyrics = value
        prefs?.edit()?.putBoolean(KEY_WORD_BY_WORD_LYRICS, value)?.apply()
    }

    fun updateLyricSourcePreferenceIndex(index: Int) {
        lyricSourcePreferenceIndex = index
        prefs?.edit()?.putInt(KEY_LYRIC_SOURCE, index)?.apply()
    }

    fun updateAutoUpgradeLyrics(value: Boolean) {
        autoUpgradeLyrics = value
        prefs?.edit()?.putBoolean(KEY_AUTO_UPGRADE_LYRICS, value)?.apply()
    }

    fun updateQqLyricsEnabled(value: Boolean) {
        qqLyricsEnabled = value
        prefs?.edit()?.putBoolean(KEY_QQ_LYRICS_ENABLED, value)?.apply()
    }

    fun updateKugouLyricsEnabled(value: Boolean) {
        kugouLyricsEnabled = value
        prefs?.edit()?.putBoolean(KEY_KUGOU_LYRICS_ENABLED, value)?.apply()
    }

    fun updateShowJapaneseRomaji(value: Boolean) {
        showJapaneseRomaji = value
        prefs?.edit()?.putBoolean(KEY_SHOW_JAPANESE_ROMAJI, value)?.apply()
    }

    fun updateJapaneseNoUpgrade(value: Boolean) {
        japaneseNoUpgrade = value
        prefs?.edit()?.putBoolean(KEY_JAPANESE_NO_UPGRADE, value)?.apply()
    }

    fun updateShowCastButton(value: Boolean) {
        showCastButton = value
        prefs?.edit()?.putBoolean(KEY_SHOW_CAST_BUTTON, value)?.apply()
    }

    fun updateShowQuality(value: Boolean) {
        showQuality = value
        prefs?.edit()?.putBoolean(KEY_SHOW_QUALITY, value)?.apply()
    }

    fun updatePlayerColorSchemeIndex(index: Int) {
        playerColorSchemeIndex = index
        prefs?.edit()?.putInt(KEY_PLAYER_COLOR_SCHEME, index)?.apply()
    }

    fun updateAutoCheckUpdate(value: Boolean) {
        autoCheckUpdate = value
        prefs?.edit()?.putBoolean(KEY_AUTO_CHECK_UPDATE, value)?.apply()
    }

    fun updateFadeEnabled(value: Boolean) {
        fadeEnabled = value
        prefs?.edit()?.putBoolean(KEY_FADE_ENABLED, value)?.apply()
    }

    fun updateAudioEffectPresetId(id: String) {
        audioEffectPresetId = id
        prefs?.edit()?.putString(KEY_AUDIO_EFFECT_PRESET, id)?.apply()
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

private val SanFranciscoFontFamily: FontFamily by lazy {
    FontFamily(Font(R.font.sf_pro_display_bold, weight = FontWeight.Bold))
}

/**
 * Apple Music 歌词引擎使用的 San Francisco 字体（仅含英文/符号，中文等自动回退系统字体）。
 * 内置渲染引擎使用系统字体。
 */
val AppleMusicLyricsFontFamily: FontFamily
    get() = SanFranciscoFontFamily
