package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class LyricsDurationMatchTest {
    private val track = Track("1", "歌曲", "歌手", "", "专辑", "", sourceUrl = "", imgUrl = "", duration = 200_000)

    @Test fun sixSecondsIsAllowedInBothDirectionsButAnyMoreIsRejected() {
        for (difference in listOf(-6000L, 6000L)) {
            val candidate = LyricsCandidate("歌曲", "歌手", "专辑", track.duration + difference, "boundary")
            assertNotNull(LyricsMatch.pickBestCandidate(listOf(candidate), track))
        }
        for (difference in listOf(-6001L, 6001L, 19_000L)) {
            val candidate = LyricsCandidate("歌曲", "歌手", "专辑", track.duration + difference, "invalid")
            assertNull(LyricsMatch.pickBestCandidate(listOf(candidate), track))
        }
    }

    @Test fun exactMetadataDoesNotRescueWrongDuration() {
        val wrong = LyricsCandidate("歌曲", "歌手", "专辑", 220_000L, "wrong")
        val valid = LyricsCandidate("歌曲", "歌手", "其他专辑", 201_000L, "valid")
        assertEquals("valid", LyricsMatch.pickBestCandidate(listOf(wrong, valid), track)?.extra)
    }

    @Test fun missingDurationDoesNotInventAMismatch() {
        assertTrue(LyricsMatch.durationMatches(0, 200_000))
        assertTrue(LyricsMatch.durationMatches(200_000, 0))
    }
}
