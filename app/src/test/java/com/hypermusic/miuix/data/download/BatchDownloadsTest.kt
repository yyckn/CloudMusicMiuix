package com.hypermusic.miuix.data.download

import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class BatchDownloadsTest {
    private fun song(id: String, source: String = "netease") = Track(id, "同名歌曲", "歌手", "", "专辑", "", source = source, sourceUrl = "", imgUrl = "")
    private fun entry(id: String, track: Track) = DownloadEntry(id, track, 0, null)

    @Test fun separatesConflictsByPlatformAndSongIdAcrossAllTaskStates() {
        val same = song("1")
        val crossPlatform = song("1", "qqmusic")
        val proposed = listOf(entry("new-a", same), entry("new-b", crossPlatform), entry("new-c", song("2")))
        val old = listOf(entry("a", same).copy(state = "complete"), entry("c", song("2")).copy(state = "failed"))
        val (conflicts, fresh) = partitionDownloadConflicts(proposed, old)
        assertEquals(listOf("new-a", "new-c"), conflicts.map { it.id })
        assertEquals(listOf("new-b"), fresh.map { it.id })
    }
    @Test fun duplicateRowsAndMultipleOldVersionsCountAsOneConflictingSong() {
        val track = song("1")
        val (conflicts, fresh) = partitionDownloadConflicts(listOf(entry("a", track), entry("b", track)),
            listOf(entry("low", track), entry("high", track)))
        assertEquals(1, conflicts.size)
        assertTrue(fresh.isEmpty())
        assertEquals(2, distinctDownloadTracks(listOf(track, track.copy(title = "新标题"), song("2"))).size)
    }
}
