package com.hypermusic.miuix.data.audio

import org.junit.Assert.*
import org.junit.Test

class SoundPresetLinkTest {
    @Test fun roundTripEveryBuiltInPreset() {
        for (preset in SoundPresets.all) {
            val result = SoundPresetLink.decode(SoundPresetLink.encode(preset.name, preset.effects))
            assertEquals(preset.name, result.name)
            assertEquals(preset.effects, result.effects)
        }
    }
    @Test fun roundTripFractionalValuesAndUnicode() {
        val effects = SoundEffects(bands = listOf(-12f, -3.25f, 0f, 1f, 2f, 3f, 4f, 5f, 6f, 12f), width = 1.17f, noise = .22f)
        assertEquals(effects, SoundPresetLink.decode(SoundPresetLink.encode("🎵 我的音效", effects)).effects)
    }
    @Test fun rejectMalformedLinks() {
        for (value in listOf("hypermusic://user/wy?data=abc", "hypermusic://sound?data=AAAA", "hypermusic://sound?data=abc&x=1")) {
            assertTrue(runCatching { SoundPresetLink.decode(value) }.isFailure)
        }
        assertTrue(runCatching { SoundPresetLink.encode("bad", SoundEffects(width = 3f)) }.isFailure)
    }
}
