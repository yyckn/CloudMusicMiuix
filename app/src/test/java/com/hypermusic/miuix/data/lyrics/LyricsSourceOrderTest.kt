package com.hypermusic.miuix.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test
import com.hypermusic.miuix.data.lyrics.LyricsRepository.Platform.*

class LyricsSourceOrderTest {
    @Test fun smartOrderIsStableRegardlessOfRomanizationToggle() {
        for (romaji in listOf(false, true)) {
            assertEquals(listOf(APPLE, QQ, KUGOU, NETEASE), LyricsRepository.platformOrder(0, romaji))
        }
    }

    @Test fun everyPreferencePreservesTheRemainingOrderAcrossAllFiveSources() {
        val sources = mapOf("netease" to NETEASE, "qqmusic" to QQ, "bodian" to BODIAN, "kugou" to KUGOU, "qishui" to QISHUI)
        val preferences = mapOf(0 to null, 1 to NETEASE, 2 to QQ, 3 to KUGOU, 4 to APPLE)
        for ((source, native) in sources) for ((preference, selected) in preferences) for (romaji in listOf(false, true)) {
            val base = listOf(APPLE, QQ, KUGOU, NETEASE)
            val expected = (listOfNotNull(selected) + base + native).distinct()
            assertEquals("source=$source preference=$preference romaji=$romaji", expected,
                LyricsRepository.platformOrder(preference, romaji, source))
        }
    }
}