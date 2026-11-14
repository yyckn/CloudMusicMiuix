package com.hypermusic.miuix.data.audio

import org.junit.Assert.*
import org.junit.Test

class LoudnessTest {
    @Test fun albumModeRequiresAlbumContext() {
        val tags = ReplayGainInfo().withTag("REPLAYGAIN_ALBUM_GAIN", "-7.2 dB").withTag("REPLAYGAIN_TRACK_GAIN", "-3 dB")
        assertEquals(0f, tags.gainFor(LoudnessMode.ALBUM, false), 0f)
        assertEquals(-7.2f, tags.gainFor(LoudnessMode.ALBUM, true), .001f)
        assertEquals(-3f, tags.gainFor(LoudnessMode.TRACK, false), .001f)
    }
    @Test fun positiveGainRespectsPeakAndMissingMetadata() {
        assertEquals(0f, ReplayGainInfo().gainFor(LoudnessMode.TRACK, true), 0f)
        assertEquals(0f, ReplayGainInfo(trackDb = 4f).gainFor(LoudnessMode.TRACK, true), 0f)
        assertEquals(0f, ReplayGainInfo(trackDb = 4f, trackPeak = 1f).gainFor(LoudnessMode.TRACK, true), .001f)
    }
    @Test fun smartModeAttenuatesLoudSignal() {
        val m = SmartLoudness(100, 1)
        repeat(1100) { m.observe(floatArrayOf(.8f)) }
        assertTrue(m.gainDb < -8f)
    }
}
