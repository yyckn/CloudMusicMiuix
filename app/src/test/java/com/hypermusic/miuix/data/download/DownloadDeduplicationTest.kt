package com.hypermusic.miuix.data.download

import com.google.gson.Gson
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class DownloadDeduplicationTest {
    private val track = Track("same-id", "Song", "Artist", "", "Album", "", sourceUrl = "", imgUrl = "")
    private fun entry(id: String, track: Track = this.track) = DownloadEntry(id, track, 0, null)
    @Test fun keyUsesPlatformAndIdInsteadOfTitle() {
        assertTrue(sameDownloadSong(track, track.copy(title = "Different title")))
        assertFalse(sameDownloadSong(track, track.copy(id = "other-id")))
        assertFalse(sameDownloadSong(track, track.copy(source = "qqmusic")))
    }
    @Test fun groupingPreservesEveryVersionAndPlatform() {
        val a = entry("a")
        val b = entry("b", track.copy(title = "renamed"))
        val c = entry("c", track.copy(source = "qqmusic"))
        val groups = groupLocalDownloads(listOf(a, b, c))
        assertEquals(2, groups.size)
        assertEquals(listOf("a", "b"), groups[0].map { it.id })
        assertEquals(listOf("c"), groups[1].map { it.id })
    }
    @Test fun equalKnownSizesHideComparison() {
        assertFalse(showDownloadSizeComparison(1024, listOf(1024)))
        assertFalse(showDownloadSizeComparison(1024, listOf(1024, 1024)))
        assertTrue(showDownloadSizeComparison(2048, listOf(1024)))
        assertTrue(showDownloadSizeComparison(null, listOf(1024)))
        assertFalse(showDownloadSizeComparison(null, emptyList()))
    }
    @Test fun oldRecordsHaveEmptyReplacementList() {
        val json = Gson().toJson(entry("old"))
        val root = com.google.gson.JsonParser.parseString(json).asJsonObject.apply { remove("replaces") }
        val restored = Gson().fromJson(root, DownloadEntry::class.java).withStorageDefaults()
        assertTrue(restored.replaces.isEmpty())
    }
    @Test fun taggedFileSizeDoesNotChangeDuplicateSourceComparison() {
        val completed = entry("downloaded").copy(state = "complete", total = 1024, bytes = 4096)
        assertEquals(1024L, completed.sourceAudioSize())
        assertTrue(equalDownloadSourceSizes(1024, listOf(completed.sourceAudioSize())))
        assertFalse(equalDownloadSourceSizes(2048, listOf(completed.sourceAudioSize())))
    }
    @Test fun unknownOrMissingSourceSizesCannotBeAssumedEqual() {
        assertFalse(equalDownloadSourceSizes(null, listOf(1024)))
        assertFalse(equalDownloadSourceSizes(1024, listOf(null)))
        assertFalse(equalDownloadSourceSizes(1024, emptyList()))
        assertFalse(equalDownloadSourceSizes(1024, listOf(1024, null)))
        assertTrue(equalDownloadSourceSizes(1024, listOf(1024, 1024)))
        assertNull(entry("legacy").copy(state = "complete", bytes = 4096, total = -1).sourceAudioSize())
    }
    @Test fun actualQualityIsPreferredOverRequestedQuality() {
        assertEquals("无损", entry("a").copy(quality = "无损").qualityLabel())
        assertTrue(entry("a").qualityLabel().contains("最高设置"))
    }
}
