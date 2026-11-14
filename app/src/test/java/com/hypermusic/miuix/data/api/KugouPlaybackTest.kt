package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class KugouPlaybackTest {
    @Test fun cancellationStopsQualityFallbackImmediately() = kotlinx.coroutines.runBlocking {
        val visited = mutableListOf<KugouPlayback.Quality>()
        var legacyCalled = false
        val failure = runCatching {
            KugouPlayback.selectAudio(KugouPlayback.Quality.HIGH, { quality ->
                visited += quality
                throw kotlinx.coroutines.CancellationException("cancelled")
            }, { legacyCalled = true; null })
        }.exceptionOrNull()
        assertTrue(failure is kotlinx.coroutines.CancellationException)
        assertEquals(listOf(KugouPlayback.Quality.HIGH), visited)
        assertFalse(legacyCalled)
    }
    @Test fun failedHigherQualityStillFallsBackAndPreservesErrors() = kotlinx.coroutines.runBlocking {
        val result = KugouPlayback.selectAudio(KugouPlayback.Quality.HIGH, { quality ->
            if (quality == KugouPlayback.Quality.HIGH) throw java.io.IOException("high failed")
            KugouPlayback.Audio("https://cdn.kugou.com/audio", quality, null, preview = false)
        }, { null })
        assertEquals(KugouPlayback.Quality.STANDARD, result.requestedCandidate)
        val failure = runCatching {
            KugouPlayback.selectAudio(KugouPlayback.Quality.HIGH, { throw java.io.IOException("high failed") }, { null })
        }.exceptionOrNull()
        assertEquals("high failed", failure?.message)
    }
    @Test fun prefersLowerQualityFullTrackOverHighQualityPreview() = kotlinx.coroutines.runBlocking {
        val visited = mutableListOf<KugouPlayback.Quality>()
        val result = KugouPlayback.selectAudio(KugouPlayback.Quality.HIGH, { quality ->
            visited += quality
            KugouPlayback.Audio("https://cdn.kugou.com/audio", quality, null, preview = quality == KugouPlayback.Quality.HIGH)
        }, { null })
        assertEquals(listOf(KugouPlayback.Quality.HIGH, KugouPlayback.Quality.STANDARD), visited)
        assertEquals(KugouPlayback.Quality.STANDARD, result.requestedCandidate)
        assertFalse(result.preview)
    }

    @Test fun keepsFirstPreviewWhenNoFullTrackIsAvailable() = kotlinx.coroutines.runBlocking {
        val result = KugouPlayback.selectAudio(KugouPlayback.Quality.HIGH, { quality ->
            KugouPlayback.Audio("https://cdn.kugou.com/audio", quality, null, preview = true)
        }, { null })
        assertEquals(KugouPlayback.Quality.HIGH, result.requestedCandidate)
        assertTrue(result.preview)
    }

    @Test fun keepsPreviewIfLaterFallbackFails() = kotlinx.coroutines.runBlocking {
        val result = KugouPlayback.selectAudio(KugouPlayback.Quality.HIGH, { quality ->
            if (quality == KugouPlayback.Quality.HIGH) KugouPlayback.Audio("https://cdn.kugou.com/audio", quality, null, preview = true)
            else throw java.io.IOException("fallback failed")
        }, { throw java.io.IOException("legacy failed") })
        assertTrue(result.preview)
        assertEquals(KugouPlayback.Quality.HIGH, result.requestedCandidate)
    }
    private fun json(text: String) = JsonParser.parseString(text).asJsonObject
    @Test fun parsesUrlAndKeepsActualBitrateSeparateFromRequestedQuality() {
        val result = KugouPlayback.parse(json("""{"data":{"url":["https://cdn.kugou.com/a.mp3"],"bitrate":128,"timelength":200000}}"""), KugouPlayback.Quality.HIGH, 200000)
        assertEquals(128, result?.actualBitrate)
        assertEquals(KugouPlayback.Quality.HIGH, result?.requestedCandidate)
        assertFalse(result!!.preview)
        assertEquals(128000, result.bitrateBps())
        assertEquals("128kbps", result.qualityLabel())
        assertEquals("128kbps", result.copy(actualBitrate = 128000).qualityLabel())
        assertEquals("未知音质", result.copy(actualBitrate = null).qualityLabel())
    }
    @Test fun identifiesPreviewAndRejectsInvalidAddress() {
        val response = json("""{"data":{"play_url":"https://cdn.kugou.com/a","timelength":30000}}""")
        assertTrue(KugouPlayback.parse(response, KugouPlayback.Quality.STANDARD, 200000)!!.preview)
        assertNull(KugouPlayback.parse(json("""{"data":{"url":"file:///sdcard/a"}}"""), KugouPlayback.Quality.STANDARD, 0))
        assertNull(KugouPlayback.parse(json("""{"status":2}"""), KugouPlayback.Quality.STANDARD, 0))
        assertTrue(KugouPlayback.parse(json("""{"data":{"url":"https://cdn.kugou.com/a","is_free_part":true}}"""), KugouPlayback.Quality.STANDARD, 0)!!.preview)
    }
    @Test fun legacyResponseRetainsFormatAndChecksDuration() {
        val response = json("""{"url":"https://sharefs.kugou.com/audio","bitRate":128,"timeLength":254,"extName":"mp3"}""")
        val full = KugouPlayback.parseLegacy(response, 254000)!!
        assertEquals("mp3", full.extension)
        assertEquals(128, full.actualBitrate)
        assertFalse(full.preview)
        assertTrue(KugouPlayback.parseLegacy(response, 300000)!!.preview)
    }
}
