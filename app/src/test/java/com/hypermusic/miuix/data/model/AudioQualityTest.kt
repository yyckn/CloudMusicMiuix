package com.hypermusic.miuix.data.model
import com.hypermusic.miuix.data.api.MusicPlatform
import org.junit.Assert.assertEquals
import org.junit.Test
class AudioQualityTest {
    @Test fun platformsHaveIndependentQualityLists() {
        assertEquals("Hi-Res 无损", AudioQuality.options(MusicPlatform.NETEASE).first())
        assertEquals("Hi-Res 无损", AudioQuality.options(MusicPlatform.QQ).first())
        assertEquals(192000, AudioQuality.neteaseBitrate(2))
    }
    @Test fun actualFormatTakesPrecedenceOverCompressedBitrate() {
        assertEquals("无损", AudioQuality.neteaseActual(800000, "flac"))
        assertEquals("高品质（320kbps）", AudioQuality.neteaseActual(320000, "mp3"))
        assertEquals("标准品质（128kbps）", AudioQuality.neteaseActual(128000, "mp3"))
        assertEquals("未知音质", AudioQuality.neteaseActual(0, ""))
    }
    @Test fun legacyNeteaseSettingMigratesToClosestSupportedTier() {
        assertEquals(listOf(0, 0, 1, 1, 3), (0..4).map(AudioQuality::migrateNeteaseIndex))
    }
    @Test fun qqFormatsRespectCeilingAndDescribeActualReturnedFile() {
        val candidates = QqAudioFormats.candidates(0)
        assertEquals(listOf("RS01", "F000", "M800", "M500", "C400"), candidates.map { it.prefix })
        val fallback = QqAudioFormats.match(candidates, "https://example.com/M800test.mp3?key=1")!!
        assertEquals("mp3", fallback.extension)
        assertEquals("高品质（320kbps）", fallback.quality)
        assertEquals(null, QqAudioFormats.match(QqAudioFormats.candidates(3), "M800test.mp3"))
        assertEquals("m4a", QqAudioFormats.candidates(4).single().extension)
    }
}
