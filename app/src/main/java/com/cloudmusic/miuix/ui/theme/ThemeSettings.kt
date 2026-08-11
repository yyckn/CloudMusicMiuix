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

    // 0 = 浅色模式, 1 = 深色模式, 2 = 跟随系统
    var themeModeIndex by mutableIntStateOf(2)
        private set

    var useFloatingBar by mutableStateOf(false)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        themeModeIndex = prefs?.getInt(KEY_THEME_MODE, 2) ?: 2
        useFloatingBar = prefs?.getBoolean(KEY_USE_FLOATING_BAR, false) ?: false
    }

    fun updateThemeModeIndex(index: Int) {
        themeModeIndex = index
        prefs?.edit()?.putInt(KEY_THEME_MODE, index)?.apply()
    }

    fun updateUseFloatingBar(value: Boolean) {
        useFloatingBar = value
        prefs?.edit()?.putBoolean(KEY_USE_FLOATING_BAR, value)?.apply()
    }
}
