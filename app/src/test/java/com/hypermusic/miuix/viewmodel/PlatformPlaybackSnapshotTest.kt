package com.hypermusic.miuix.viewmodel

import com.google.gson.Gson
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformPlaybackSnapshotTest {
    private val gson = Gson()
    private val song = Track(id = "qq-mid", title = "测试", artist = "歌手", artistId = "qqartist_id",
        album = "专辑", albumId = "1", source = "qqmusic", sourceUrl = "", imgUrl = "")

    @Test fun oldReleaseFieldsRestoreTypedTracksAndPlaybackPosition() {
        val songJson = gson.toJson(song)
        val json = """{"a":$songJson,"b":[$songJson],"c":[$songJson],"d":42000,"e":1,"f":true,"h":true,"i":[$songJson],"j":0,"k":[$songJson],"l":0,"m":"seed","n":"list"}"""
        val snapshot = gson.fromJson(json, PlatformPlaybackSnapshot::class.java)
        assertEquals(song, snapshot.track)
        assertEquals(song, snapshot.playlist.single())
        assertEquals(song, snapshot.original.single())
        assertEquals(song, snapshot.fmTracks.single())
        assertEquals(song, snapshot.intelligenceTracks.single())
        assertEquals(42000L, snapshot.position)
        assertTrue(snapshot.fm)
        assertTrue(snapshot.guess)
        assertEquals("list", snapshot.playlistId)
    }

    @Test fun newSnapshotsUseStableNamesAndRoundTrip() {
        val original = PlatformPlaybackSnapshot(track = song, playlist = listOf(song), position = 1000)
        val json = gson.toJson(original)
        assertTrue(json.contains("\"playlist\""))
        assertEquals(original, gson.fromJson(json, PlatformPlaybackSnapshot::class.java))
    }
}
