package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.LyricLine
import com.hypermusic.miuix.data.model.SongLyrics
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NativeLyricsFallbackTest {
    private val native = PlatformLyricResult(SongLyrics(listOf(LyricLine("原歌词", startAt = 0))), false, "netease")

    @Test fun aFastNativeResponseWaits1300MillisecondsBeforeBecomingTheProvisionalResult() = runBlocking {
        val updates = mutableListOf<PlatformLyricResult>()
        val start = System.nanoTime()
        val job = launch { LyricsRepository.publishNativeFallback(CompletableDeferred(native)) { updates += it } }
        delay(100)
        assertTrue("native lyrics must not flash immediately", updates.isEmpty())
        withTimeout(5000) { job.join() }
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 1300)
        assertEquals(listOf(native), updates)
    }

    @Test fun finishingSelectionOrSwitchingSongsCancelsTheDelayedFallback() = runBlocking {
        val updates = mutableListOf<PlatformLyricResult>()
        val job = launch { LyricsRepository.publishNativeFallback(CompletableDeferred(native)) { updates += it } }
        yield()
        job.cancelAndJoin()
        assertTrue(updates.isEmpty())
    }
}
