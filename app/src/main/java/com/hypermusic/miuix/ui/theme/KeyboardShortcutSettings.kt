package com.hypermusic.miuix.ui.theme

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
    SeekBackward("后退5秒", KeyEvent.KEYCODE_DPAD_LEFT),
    SeekForward("快进5秒", KeyEvent.KEYCODE_DPAD_RIGHT),
    Like("收藏歌曲", KeyEvent.KEYCODE_S),
    More("打开更多面板", KeyEvent.KEYCODE_M),
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
    var modifiers by mutableStateOf<Map<ShortcutAction, Int>>(emptyMap())
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean(KEY_ENABLED, true)
        keys = ShortcutAction.entries.associateWith { p.getInt(keyName(it), it.defaultKeyCode) }
        modifiers = ShortcutAction.entries.associateWith { normalizeModifiers(p.getInt("modifiers_${it.name}", 0)) }
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
    }

    fun keyFor(action: ShortcutAction): Int = keys[action] ?: action.defaultKeyCode

    fun updateKey(action: ShortcutAction, keyCode: Int, metaState: Int = 0) {
        keys = keys + (action to keyCode)
        val normalized = normalizeModifiers(metaState)
        modifiers = modifiers + (action to normalized)
        prefs?.edit()?.putInt(keyName(action), keyCode)?.putInt("modifiers_${action.name}", normalized)?.apply()
    }

    fun matches(action: ShortcutAction, keyCode: Int, metaState: Int): Boolean =
        keyFor(action) == keyCode && (modifiers[action] ?: 0) == normalizeModifiers(metaState)

    fun normalizeModifiers(state: Int): Int =
        (if (state and (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_RIGHT_ON) != 0) KeyEvent.META_CTRL_ON else 0) or
        (if (state and (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON) != 0) KeyEvent.META_SHIFT_ON else 0) or
        (if (state and (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_RIGHT_ON) != 0) KeyEvent.META_ALT_ON else 0)

    fun isModifierKey(keyCode: Int): Boolean = keyCode in setOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT, KeyEvent.KEYCODE_FUNCTION, KeyEvent.KEYCODE_UNKNOWN)

    fun keyLabel(action: ShortcutAction): String = buildList {
        val state = modifiers[action] ?: 0
        if (state and KeyEvent.META_CTRL_ON != 0) add("Ctrl")
        if (state and KeyEvent.META_SHIFT_ON != 0) add("Shift")
        if (state and KeyEvent.META_ALT_ON != 0) add("Alt")
        add(keyCodeLabel(keyFor(action)))
    }.joinToString(" + ")

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
