package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.lyrics.LyricsRepository.Platform.*
import com.hypermusic.miuix.data.model.SongLyrics
import com.hypermusic.miuix.data.model.LyricLine
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LyricsSelectionTest {
    private fun result(name: String, word: Boolean = false, roman: Boolean = false) =
        PlatformLyricResult(SongLyrics(listOf(LyricLine("歌词", startAt = 0, phonetic = if (roman) "romaji" else ""))), word, name)
    private val order = listOf(APPLE, QQ, KUGOU, NETEASE)

    @Test fun emptyResultsDoNotBlockOtherSourcesEvenWithoutUpgrade() = runBlocking {
        val outcome = LyricsRepository.selectLyrics(listOf(NETEASE, QQ), false) {
            if (it == NETEASE) PlatformLyricResult(SongLyrics(isPureMusic = true), false, "netease") else result("qq")
        }
        assertEquals("qq", outcome?.result?.platform)
    }

    @Test fun instrumentalFlagSurvivesWhenOtherProvidersHaveNoLyrics() = runBlocking {
        for (upgrade in listOf(false, true)) {
            val value = LyricsRepository.selectLyrics(order, upgrade, romanization = true, source = "netease") {
                if (it == NETEASE) PlatformLyricResult(SongLyrics(isPureMusic = true), false, "netease") else null
            }
            assertEquals("netease", value?.result?.platform)
            assertTrue(value!!.result.lyrics.isPureMusic)
        }
    }

    @Test fun instrumentalFallbackDoesNotHideRealLyricsFromALowerPrioritySource() = runBlocking {
        val value = LyricsRepository.selectLyrics(order, false) {
            when (it) {
                APPLE -> PlatformLyricResult(SongLyrics(isPureMusic = true), false, "apple")
                NETEASE -> result("netease")
                else -> null
            }
        }
        assertEquals("netease", value?.result?.platform)
    }

    @Test fun genuinelyEmptyResultsStillMeanNoLyrics() = runBlocking {
        val value = LyricsRepository.selectLyrics(order, true) {
            PlatformLyricResult(SongLyrics(), false, it.name)
        }
        assertNull(value)
    }

    @Test fun allProvidersStartConcurrentlyButResponseSpeedDoesNotChangePriority() = runBlocking {
        val started = order.associateWith { CompletableDeferred<Unit>() }
        val releaseApple = CompletableDeferred<Unit>()
        val selection = async {
            LyricsRepository.selectLyrics(order, true) { platform ->
                started.getValue(platform).complete(Unit)
                if (platform == APPLE) releaseApple.await()
                result(platform.name, word = true)
            }
        }
        withTimeout(2000) { started.values.forEach { it.await() } }
        assertFalse(selection.isCompleted)
        releaseApple.complete(Unit)
        assertEquals("APPLE", selection.await()?.result?.platform)
    }

    @Test fun upgradeFindsFirstWordResultAndKeepsFirstPlainIfNone() = runBlocking {
        val value = LyricsRepository.selectLyrics(order, true) { platform ->
            when (platform) { APPLE -> null; QQ -> result("qq"); KUGOU -> result("kugou", true); else -> result("netease", true) }
        }
        assertEquals("kugou", value?.result?.platform)
        assertTrue(value!!.upgraded)
        val plain = LyricsRepository.selectLyrics(order, true) { result(it.name) }
        assertEquals("APPLE", plain?.result?.platform)
        assertFalse(plain!!.upgraded)
        val disabled = LyricsRepository.selectLyrics(order, false) { result(it.name, word = it != APPLE) }
        assertEquals("APPLE", disabled?.result?.platform)
    }

    @Test fun qqRomanizationWinsEvenWithNeteasePreferenceAndCancelsPendingSources() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val value = withTimeout(2000) {
            LyricsRepository.selectLyrics(listOf(NETEASE, APPLE, QQ, KUGOU), true, romanization = true) {
                when (it) {
                    APPLE -> { started.complete(Unit); try { awaitCancellation() } finally { cancelled.complete(Unit) } }
                    NETEASE -> result("netease", word = true, roman = true)
                    QQ -> { started.await(); result("qq", roman = true) }
                    else -> awaitCancellation()
                }
            }
        }
        assertEquals("qq", value?.result?.platform)
        assertTrue(cancelled.isCompleted)
    }

    @Test fun neteasePlainRomanizationBeatsQqWordLyricsWithoutRomanization() = runBlocking {
        val value = LyricsRepository.selectLyrics(listOf(QQ, APPLE, KUGOU, NETEASE), true, romanization = true) {
            result(it.name, word = it != NETEASE, roman = it == NETEASE)
        }
        assertEquals("NETEASE", value?.result?.platform)
    }

    @Test fun bothRomanizationsPreferSongSourceOtherwiseQqRegardlessOfWordTimingOrPreference() = runBlocking {
        for (source in listOf("netease", "qqmusic", "kugou", "bodian", "qishui")) {
            val expected = if (source == "netease") NETEASE else QQ
            for (upgrade in listOf(false, true)) {
                val value = LyricsRepository.selectLyrics(listOf(APPLE, NETEASE, QQ, KUGOU), upgrade,
                    romanization = true, source = source) {
                    result(it.name, word = it != expected, roman = it == NETEASE || it == QQ)
                }
                assertEquals("source=$source upgrade=$upgrade", expected.name, value?.result?.platform)
            }
        }
    }

    @Test fun nativeSourceWithoutRomanizationStillUsesOtherProvidersRomanization() = runBlocking {
        for (source in listOf("netease", "qqmusic")) {
            val native = if (source == "netease") NETEASE else QQ
            val other = if (native == NETEASE) QQ else NETEASE
            val value = LyricsRepository.selectLyrics(order, true, romanization = true, source = source) {
                result(it.name, word = it == native, roman = it == other)
            }
            assertEquals(other.name, value?.result?.platform)
        }
    }

    @Test fun fastQqResultWaitsForNeteaseSongNativeRomanization() = runBlocking {
        val qqReturned = CompletableDeferred<Unit>()
        val releaseNetease = CompletableDeferred<Unit>()
        val selection = async {
            LyricsRepository.selectLyrics(order, true, romanization = true, source = "netease") {
                when (it) {
                    QQ -> { qqReturned.complete(Unit); result("qq", word = true, roman = true) }
                    NETEASE -> { releaseNetease.await(); result("netease", roman = true) }
                    else -> awaitCancellation()
                }
            }
        }
        withTimeout(2000) { qqReturned.await() }
        assertFalse(selection.isCompleted)
        releaseNetease.complete(Unit)
        assertEquals("netease", withTimeout(2000) { selection.await() }?.result?.platform)
    }

    @Test fun romanizationToggleAloneDoesNotReorderResultsWithoutActualRomanization() = runBlocking {
        val value = LyricsRepository.selectLyrics(order, false, romanization = true) { result(it.name) }
        assertEquals("APPLE", value?.result?.platform)
        val disabled = LyricsRepository.selectLyrics(order, false, romanization = false) { result(it.name, roman = it == QQ) }
        assertEquals("APPLE", disabled?.result?.platform)
    }

    @Test fun preferenceWinsAmongWordLyricsWhenNoRomanizationMatches() = runBlocking {
        val value = LyricsRepository.selectLyrics(listOf(KUGOU, APPLE, QQ, NETEASE), true, romanization = true) { result(it.name, word = true) }
        assertEquals("KUGOU", value?.result?.platform)
    }

    @Test fun cancellationIsPropagated() = runBlocking {
        val error = runCatching { LyricsRepository.selectLyrics(listOf(QQ), true) { throw CancellationException("cancelled") } }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }

    @Test fun sourceFailureFallsBackAndStillFindsWordLyrics() = runBlocking {
        val value = LyricsRepository.selectLyrics(listOf(QISHUI, APPLE, QQ), true) {
            when (it) {
                QISHUI -> throw IllegalStateException("unavailable")
                APPLE -> result("apple")
                else -> result("qq", true)
            }
        }
        assertEquals("qq", value?.result?.platform)
        assertTrue(value!!.upgraded)
    }
}
