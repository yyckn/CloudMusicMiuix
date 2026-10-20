package com.musichub.miuix.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ShortcutAction(val title: String, val defaultKeyCode: Int) {
    PlayPause("暂停/播放歌曲", KeyEvent.KEYCODE_SPACE),
    Previous("上一首", KeyEvent.KEYCODE_DPAD_UP),
    Next("下一首", KeyEvent.KEYCODE_DPAD_DOWN),
    SeekBackward("后退10秒", KeyEvent.KEYCODE_DPAD_LEFT),
    SeekForward("快进10秒", KeyEvent.KEYCODE_DPAD_RIGHT),
    Like("收藏歌曲", KeyEvent.KEYCODE_S),
}

object KeyboardShortcutSettings {
    private const val PREFS_NAME = "keyboard_shortcuts"
    private const val KEY_ENABLED = "enabled"

    private fun keyName(action: ShortcutAction) = "key_${action.name}"

    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    var keys by mutableStateOf<Map<ShortcutAction, Int>>(
        ShortcutAction.entries.associateWith { it.defaultKeyCode }
    )
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean(KEY_ENABLED, true)
        keys = ShortcutAction.entries.associateWith { p.getInt(keyName(it), it.defaultKeyCode) }
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
    }

    fun keyFor(action: ShortcutAction): Int = keys[action] ?: action.defaultKeyCode

    fun updateKey(action: ShortcutAction, keyCode: Int) {
        keys = keys + (action to keyCode)
        prefs?.edit()?.putInt(keyName(action), keyCode)?.apply()
    }

    fun keyLabel(action: ShortcutAction): String = keyCodeLabel(keyFor(action))

    fun keyCodeLabel(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_SPACE -> "空格键"
        KeyEvent.KEYCODE_DPAD_UP -> "方向上键"
        KeyEvent.KEYCODE_DPAD_DOWN -> "方向下键"
        KeyEvent.KEYCODE_DPAD_LEFT -> "方向左键"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "方向右键"
        KeyEvent.KEYCODE_ENTER -> "回车键"
        KeyEvent.KEYCODE_TAB -> "Tab 键"
        KeyEvent.KEYCODE_DEL -> "退格键"
        KeyEvent.KEYCODE_ESCAPE -> "Esc 键"
        KeyEvent.KEYCODE_DPAD_CENTER -> "方向中键"
        KeyEvent.KEYCODE_A -> "A"
        KeyEvent.KEYCODE_B -> "B"
        KeyEvent.KEYCODE_C -> "C"
        KeyEvent.KEYCODE_D -> "D"
        KeyEvent.KEYCODE_E -> "E"
        KeyEvent.KEYCODE_F -> "F"
        KeyEvent.KEYCODE_G -> "G"
        KeyEvent.KEYCODE_H -> "H"
        KeyEvent.KEYCODE_I -> "I"
        KeyEvent.KEYCODE_J -> "J"
        KeyEvent.KEYCODE_K -> "K"
        KeyEvent.KEYCODE_L -> "L"
        KeyEvent.KEYCODE_M -> "M"
        KeyEvent.KEYCODE_N -> "N"
        KeyEvent.KEYCODE_O -> "O"
        KeyEvent.KEYCODE_P -> "P"
        KeyEvent.KEYCODE_Q -> "Q"
        KeyEvent.KEYCODE_R -> "R"
        KeyEvent.KEYCODE_S -> "S"
        KeyEvent.KEYCODE_T -> "T"
        KeyEvent.KEYCODE_U -> "U"
        KeyEvent.KEYCODE_V -> "V"
        KeyEvent.KEYCODE_W -> "W"
        KeyEvent.KEYCODE_X -> "X"
        KeyEvent.KEYCODE_Y -> "Y"
        KeyEvent.KEYCODE_Z -> "Z"
        else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }
}
