package com.hypermusic.miuix.data.download

import com.google.gson.Gson
import com.hypermusic.miuix.data.lyrics.RawLyricDocument
import com.hypermusic.miuix.data.lyrics.LyricExclusionRules
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class OfflineDocumentTest {
    private val track = Track("1", "测试歌曲", "歌手", "", "专辑", "", sourceUrl = "", imgUrl = "")
    @Test fun exportUsesTitleArtistAndActualExtension() {
        assertEquals("测试歌曲 - 歌手.flac", exportedSongName(track, "FLAC"))
        assertEquals("A_B - C_D.mp3", exportedSongName(track.copy(title = "A/B", artist = "C:D"), "mp3"))
    }
    @Test fun olderSavedTracksHaveNoLocalMarker() {
        val old = Gson().toJson(track).replace(Regex(",?\"localDownloadId\":null"), "")
        assertTrue(Gson().fromJson(old, Track::class.java).localDownloadId.isNullOrBlank())
    }
    @Test fun preMultiPlatformDownloadRecordsCanBeCopiedIntoLocalTracks() {
        val json = """{"id":"12345678-1234-1234-1234-123456789012","track":{"id":"old-song","title":"旧版歌曲","artist":"歌手","album":"专辑","source":"netease","sourceUrl":"","imgUrl":""},"qualityIndex":0,"state":"complete","path":"/old/song.mp3"}"""
        val raw = Gson().fromJson(json, DownloadEntry::class.java)
        val entry = raw.withStorageDefaults()
        val local = OfflineLibrary.localTrack(entry)
        assertEquals("旧版歌曲", local.title)
        assertEquals("", local.platform.bodianFreeSign)
        assertEquals("", local.platform.kugouHash)
        assertEquals("", local.platform.kugouAlbumAudioId)
        assertEquals("", local.platform.qqMediaMid)
        assertTrue(local.artistNames.isEmpty())
        assertEquals("", local.artistId)
        assertEquals("", local.albumId)
        assertEquals("", entry.message)
        assertEquals("", entry.format)
        assertEquals(entry.id, local.localDownloadId)
        // The conversion itself is defensive, even outside the download-record loader.
        assertEquals("", OfflineLibrary.localTrack(raw).platform.bodianFreeSign)
    }
    @Test fun explicitNullFieldsAreNormalizedWithoutLosingTheSong() {
        val json = """{"id":"song","title":"Test","artist":null,"album":null,"source":null,"artistNames":[null,"Singer"],"bodianFreeSign":null}"""
        val parsed = Gson().fromJson(json, Track::class.java).withStorageDefaults()
        assertEquals("netease", parsed.source)
        assertEquals(listOf("Singer"), parsed.artistNames)
        assertEquals("", parsed.copy(url = null).artist)
        assertEquals("", parsed.platform.bodianFreeSign)
    }
    @Test fun rawTextRoundTripsWithoutFiltering() {
        val original = "[00:01.00]作词：有人\n[00:03.00]Hello world\n[00:05.00]Second line"
        val doc = RawLyricDocument("lrc", original, "[00:03.00]你好世界")
        val copy = Gson().fromJson(Gson().toJson(doc), RawLyricDocument::class.java)
        assertEquals(original, copy.original)
        val wasEnabled = LyricExclusionRules.enabled
        try {
            LyricExclusionRules.updateEnabled(true)
            assertFalse(copy.parse(track).lines.any { it.text.contains("作词") })
            LyricExclusionRules.updateEnabled(false)
            assertTrue(copy.parse(track).lines.any { it.text.contains("作词") })
            assertEquals(original, copy.original)
        } finally { LyricExclusionRules.updateEnabled(wasEnabled) }
    }
    @Test fun qrcKeepsWordTimingsAndTranslation() {
        val doc = RawLyricDocument("qrc", "[1000,1000]你(1000,500)好(1500,500)", "[00:01.00]Hello")
        val line = doc.parse(track).lines.first()
        assertEquals("你好", line.text)
        assertEquals(2, line.words!!.size)
        assertEquals("Hello", line.translation)
    }
    @Test fun ttmlKeepsDuetAndBackingMetadata() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div><p begin="1s" end="3s" ttm:agent="v2">Hello<span ttm:role="x-bg">(backing)</span></p></div></body></tt>"""
        val parsed = RawLyricDocument("ttml", xml).parse(track)
        assertEquals("v2", parsed.lines.first().amllAgent)
        assertTrue(parsed.lines.any { it.isBackingVocal })
    }
}
