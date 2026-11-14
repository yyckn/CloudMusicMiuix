package com.hypermusic.miuix.data.lyrics

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricMatchingDeviceTest {
    private fun song(id: String, source: String) = Track(id = id, title = "Test song", artist = "Test artist", artistId = "", album = "Test album", albumId = "", source = source, sourceUrl = "", imgUrl = "", duration = 123000)

    @Test fun matchingKeysIncludeOriginalPlatformAndPersistCompleteTarget() {
        LyricMatches.init(ApplicationProvider.getApplicationContext())
        val first = song("lyric-test-id", "netease")
        val second = song("lyric-test-id", "qqmusic")
        val target = song("lyric-test-target", "apple")
        try {
            LyricMatches.save(first, "apple", target)
            assertEquals(target, LyricMatches.find(first)?.match)
            assertNull(LyricMatches.find(second))
            val prefs = ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("lyric_matches", android.content.Context.MODE_PRIVATE)
            assertTrue(prefs.getString("entries", "")!!.contains("lyric-test-target"))
            LyricMatches.save(second, "netease", first)
            LyricMatches.remove(first)
            assertNotNull(LyricMatches.find(second))
        } finally { LyricMatches.remove(first); LyricMatches.remove(second) }
    }

    @Test fun amllDatabaseReturnsRealTimedLyrics() = runBlocking {
        val result = AmllLyricsApi.fetch(song("netrack_2116106898", "netease"), exactApple = true)
        assertNotNull("AMLL TTML DB did not return the known public sample", result)
        assertTrue(result!!.lyrics.lines.isNotEmpty())
        assertTrue(result.lyrics.lines.any { !it.words.isNullOrEmpty() })
    }
    @Test fun appleIndexedTtmlIsAvailable() = runBlocking {
        val apple = song("1645308331", "apple")
        val result = AmllLyricsApi.fetch(apple, exactApple = true)
        assertNotNull(result)
        assertTrue(result!!.lyrics.lines.isNotEmpty())
        val original = song("test-apple-exact-match", "qqmusic")
        LyricMatches.init(ApplicationProvider.getApplicationContext())
        try {
            LyricMatches.save(original, "apple", apple)
            val loaded = LyricsRepository.load(original).getOrThrow()
            assertEquals("Apple Music", loaded.source)
            assertTrue(loaded.lyrics.lines.isNotEmpty())
        } finally { LyricMatches.remove(original) }
    }

    @Test fun publicMusicSearchesReturnCandidates() = runBlocking {
        val original = song("test-public", "netease").copy(title = "晴天", artist = "周杰伦", duration = 269000)
        for (source in listOf("netease", "qq", "kugou", "apple")) {
            val results = LyricsRepository.searchMatches(source, original)
            assertTrue("$source search returned no candidates", results.isNotEmpty())
            assertTrue("$source search lacks song identifiers", results.any { it.id.isNotBlank() })
        }
    }

    @Test fun additionalPlatformsFindAmllLyricsByMetadata() = runBlocking {
        for (source in listOf("bodian", "kugou", "qishui")) {
            val original = song("2116106898", source).copy(title = "晴天", artist = "周杰伦", album = "叶惠美", duration = 269000)
            val result = AmllLyricsApi.fetch(original)
            assertNotNull("$source 应能按歌曲信息匹配 AMLL 歌词", result)
            assertTrue(result!!.lyrics.lines.isNotEmpty())
        }
    }

    @Test fun chosenCandidatesLoadFromExactSources() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        LyricMatches.init(ctx)
        com.hypermusic.miuix.ui.theme.ThemeSettings.init(ctx)
        LyricExclusionRules.init(ctx)
        val original = song("test-exact-matches", "netease").copy(title = "晴天", artist = "周杰伦", duration = 269000)
        try {
            for ((source, label) in listOf("netease" to "网易云音乐", "qq" to "QQ音乐", "kugou" to "酷狗音乐")) {
                val candidate = LyricsRepository.searchMatches(source, original).first()
                LyricMatches.save(original, source, candidate)
                val loaded = LyricsRepository.load(original)
                assertTrue("$source selected match failed: ${loaded.exceptionOrNull()}", loaded.isSuccess)
                assertEquals(label, loaded.getOrThrow().source)
                assertTrue(loaded.getOrThrow().lyrics.lines.isNotEmpty())
                assertTrue("$source 未保存当前使用的原始歌词", loaded.getOrThrow().rawDebugText.isNotBlank())
                assertEquals(candidate.id, loaded.getOrThrow().matchedTrack?.id)
            }
        } finally { LyricMatches.remove(original) }
    }

    @Test fun amllSearchResultsLoadTheSelectedDatabaseEntry() = runBlocking {
        val original = song("test-amll-search", "netease").copy(title = "晴天", artist = "周杰伦", album = "叶惠美", duration = 269000)
        val candidates = AmllLyricsApi.search(original)
        assertTrue("词库应返回已有歌词的候选", candidates.isNotEmpty())
        assertTrue(candidates.all { it.id.startsWith("amll_") })
        val selected = candidates.first()
        val lyrics = AmllLyricsApi.fetch(selected, exactApple = true)
        assertNotNull("选中的 AMLL 条目应能获取歌词", lyrics)
        assertTrue(lyrics!!.lyrics.lines.isNotEmpty())
        assertTrue(lyrics.rawDebugText.contains("<tt"))
        LyricMatches.init(ApplicationProvider.getApplicationContext())
        try {
            LyricMatches.save(original, "apple", selected)
            val loaded = LyricsRepository.load(original).getOrThrow()
            assertEquals("Apple Music", loaded.source)
            assertEquals(selected.id, loaded.matchedTrack?.id)
            assertTrue(loaded.rawDebugText.contains("<tt"))
        } finally { LyricMatches.remove(original) }
    }

    @Test fun oldAppleCatalogueMatchesCanFindAnExactDatabaseEntry() = runBlocking {
        val oldMatch = song("999999999999999", "apple").copy(title = "晴天", artist = "周杰伦", album = "叶惠美", duration = 269000)
        assertNotNull("旧版 Apple 歌曲匹配应按精确元数据回查 AMLL", AmllLyricsApi.fetch(oldMatch, exactApple = true))
    }

}
