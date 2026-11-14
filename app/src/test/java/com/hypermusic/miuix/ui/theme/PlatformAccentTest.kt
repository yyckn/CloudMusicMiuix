package com.hypermusic.miuix.ui.theme

import androidx.compose.ui.graphics.Color
import com.hypermusic.miuix.data.api.MusicPlatform
import org.junit.Assert.*
import org.junit.Test

class PlatformAccentTest {
    @Test fun insertingSourceOptionDoesNotChangeExistingSavedColors() {
        assertEquals(0, accentMenuIndex(0))
        assertEquals(1, accentMenuIndex(ThemeSettings.SOURCE_ACCENT_INDEX))
        for (saved in 1..15) assertEquals(saved + 1, accentMenuIndex(saved))
        for (saved in 0..ThemeSettings.SOURCE_ACCENT_INDEX) {
            assertEquals(saved, accentSavedIndex(accentMenuIndex(saved)))
        }
    }

    @Test fun eachPlatformHasTheRequestedPalette() {
        assertEquals(Color(0xFFFF0000), platformAccent(MusicPlatform.NETEASE).primary)
        assertEquals(PlatformAccent(Color(0xFF1ABF75)), platformAccent(MusicPlatform.QQ))
        assertEquals(Color(0xFF05E8B2), platformAccent(MusicPlatform.BODIAN).primary)
        assertEquals(Color(0xFF0062FF), platformAccent(MusicPlatform.KUGOU).primary)
        assertEquals(PlatformAccent(Color(0xFF24FEC3), Color(0xFF41FE43), Color(0xFFDDFC46)), platformAccent(MusicPlatform.QISHUI))
    }
}
