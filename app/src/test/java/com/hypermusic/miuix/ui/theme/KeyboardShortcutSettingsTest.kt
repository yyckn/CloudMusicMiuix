package com.hypermusic.miuix.ui.theme

import android.view.KeyEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class KeyboardShortcutSettingsTest {
    private val original = ShortcutAction.entries.associateWith { KeyboardShortcutSettings.keyFor(it) to (KeyboardShortcutSettings.modifiers[it] ?: 0) }
    @After fun restore() { original.forEach { (action, binding) -> KeyboardShortcutSettings.updateKey(action, binding.first, binding.second) } }

    @Test fun sameMainKeyCanHaveDifferentModifierBindings() {
        KeyboardShortcutSettings.updateKey(ShortcutAction.More, KeyEvent.KEYCODE_M)
        KeyboardShortcutSettings.updateKey(ShortcutAction.Like, KeyEvent.KEYCODE_M, KeyEvent.META_CTRL_ON)
        assertTrue(KeyboardShortcutSettings.matches(ShortcutAction.More, KeyEvent.KEYCODE_M, 0))
        assertFalse(KeyboardShortcutSettings.matches(ShortcutAction.More, KeyEvent.KEYCODE_M, KeyEvent.META_CTRL_ON))
        assertTrue(KeyboardShortcutSettings.matches(ShortcutAction.Like, KeyEvent.KEYCODE_M, KeyEvent.META_CTRL_RIGHT_ON))
        assertEquals("Ctrl + M", KeyboardShortcutSettings.keyLabel(ShortcutAction.Like))
    }

    @Test fun allThreeModifiersCanBeCombinedAndMustMatchExactly() {
        val modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON
        KeyboardShortcutSettings.updateKey(ShortcutAction.More, KeyEvent.KEYCODE_F8, modifiers)
        assertTrue(KeyboardShortcutSettings.matches(ShortcutAction.More, KeyEvent.KEYCODE_F8,
            KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON or KeyEvent.META_ALT_RIGHT_ON))
        assertFalse(KeyboardShortcutSettings.matches(ShortcutAction.More, KeyEvent.KEYCODE_F8, KeyEvent.META_CTRL_ON))
        assertFalse(KeyboardShortcutSettings.matches(ShortcutAction.More, KeyEvent.KEYCODE_F9, modifiers))
    }

    @Test fun modifierKeysAreNotCapturedAsTheMainKey() {
        assertTrue(KeyboardShortcutSettings.isModifierKey(KeyEvent.KEYCODE_CTRL_LEFT))
        assertTrue(KeyboardShortcutSettings.isModifierKey(KeyEvent.KEYCODE_ALT_RIGHT))
        assertTrue(KeyboardShortcutSettings.isModifierKey(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertFalse(KeyboardShortcutSettings.isModifierKey(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(KeyEvent.KEYCODE_M, ShortcutAction.More.defaultKeyCode)
    }
}
