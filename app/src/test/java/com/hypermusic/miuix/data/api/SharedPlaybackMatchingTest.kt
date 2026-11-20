package com.hypermusic.miuix.data.api

import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class SharedPlaybackMatchingTest {
    private fun track(title: String = "晴天", artist: String = "周杰伦", artists: List<String> = emptyList(), source: String = "qqmusic", id: String = "qq-mid") =
        Track(id, title, artist, "", "专辑", "", source, "", "", artistNames = artists)

    @Test fun exactTitlesAndAllSingersMustMatchAcrossIndependentIds() {
        val original = track()
        assertTrue(SharedPlaybackAccounts.sameSong(original, track(source = "bodian", id = "bodian_123")))
        assertTrue(SharedPlaybackAccounts.sameSong(original, track(title = "<em>晴天</em>", artist = "<em>周杰伦</em>")))
        assertFalse(SharedPlaybackAccounts.sameSong(original, track(title = "晴天 (Live)")))
        assertFalse(SharedPlaybackAccounts.sameSong(original, track(title = "晴天 Remix")))
        assertFalse(SharedPlaybackAccounts.sameSong(original, track(artist = "翻唱歌手")))
        assertFalse(SharedPlaybackAccounts.sameSong(original, track(artist = "周杰伦 / 另一位歌手")))
        assertFalse(SharedPlaybackAccounts.sameSong(track(title = ""), track(title = "")))
        assertFalse(SharedPlaybackAccounts.sameSong(track(artist = ""), track(artist = "")))
    }

    @Test fun singerOrderAndFormattingCanDifferButNoSingerCanBeMissing() {
        val first = track(title = "  ＡＢＣ  ", artists = listOf("Singer A", "Singer B"))
        assertTrue(SharedPlaybackAccounts.sameSong(first, track(title = "ABC", artists = listOf("Singer B", "Singer A"))))
        assertTrue(SharedPlaybackAccounts.sameSong(first, track(title = "abc", artist = "Singer B、Singer A")))
        assertFalse(SharedPlaybackAccounts.sameSong(first, track(title = "ABC", artists = listOf("Singer A"))))
    }

    @Test fun qualityCeilingFallsBackToTheTargetPlatformWithoutUpgradingLossyMusic() {
        assertEquals(listOf(0, 0, 1, 2, 2), (0..4).map { SharedPlaybackAccounts.qualityIndex("qqmusic", "bodian", it) })
        assertEquals(listOf(1, 2, 3), (0..2).map { SharedPlaybackAccounts.qualityIndex("bodian", "qqmusic", it) })
        assertEquals(0, SharedPlaybackAccounts.qualityIndex("qqmusic", "qqmusic", 0))
    }
}
