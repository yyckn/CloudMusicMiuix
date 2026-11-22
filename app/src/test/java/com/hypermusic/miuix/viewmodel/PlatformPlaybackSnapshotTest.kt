package com.hypermusic.miuix.viewmodel

import com.google.gson.Gson
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.data.api.MusicPlatform
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
        // Obsolete mode fields in old saves are ignored; the queue and position still restore.
        assertTrue(!gson.toJson(snapshot).contains("\"guess\""))
        assertEquals("list", snapshot.playlistId)
    }

    @Test fun newSnapshotsUseStableNamesAndRoundTrip() {
        val original = PlatformPlaybackSnapshot(track = song, playlist = listOf(song), position = 1000)
        val json = gson.toJson(original)
        assertTrue(json.contains("\"playlist\""))
        assertEquals(original, gson.fromJson(json, PlatformPlaybackSnapshot::class.java))
    }

    @Test fun newPlatformPlaybackIdentitySurvivesSnapshotRoundTrip() {
        val tracks = listOf(
            song.copy(id = "bodian_123", source = "bodian", bodianFreeSign = "sample-sign"),
            song.copy(id = "kugou_456", source = "kugou", kugouHash = "ABCDEF0123456789ABCDEF0123456789", kugouAlbumAudioId = "456"),
            song.copy(id = "qishui_789", source = "qishui")
        )
        for (track in tracks) {
            val original = PlatformPlaybackSnapshot(track = track, playlist = listOf(track), position = 2500)
            val restored = gson.fromJson(gson.toJson(original), PlatformPlaybackSnapshot::class.java)
            assertEquals(original, restored)
            assertEquals(track.source, restored.track?.source)
            assertEquals(track, restored.playlist.single())
        }
    }

    @Test fun snapshotsCannotRestoreSongsFromAnotherPlatform() {
        val qq = song
        val bodian = song.copy(id = "bodian_123", source = "bodian")
        assertTrue(PlatformPlaybackSnapshot(track = bodian, playlist = listOf(bodian)).belongsTo(MusicPlatform.BODIAN))
        assertTrue(!PlatformPlaybackSnapshot(track = qq, playlist = listOf(bodian)).belongsTo(MusicPlatform.BODIAN))
        assertTrue(!PlatformPlaybackSnapshot(track = bodian, playlist = listOf(qq)).belongsTo(MusicPlatform.BODIAN))
        assertTrue(!PlatformPlaybackSnapshot(track = bodian, fmTracks = listOf(qq)).belongsTo(MusicPlatform.BODIAN))
    }

    @Test fun sceneRadioModeSurvivesRestartAndCannotCrossPlatforms() {
        val track = song.copy(id = "qishui_123", source = "qishui")
        val snapshot = PlatformPlaybackSnapshot(track = track, playlist = listOf(track), fm = true,
            fmTracks = listOf(track), additionalRadioId = "extra:qishui:scene:6", fmModeLabel = "专注模式")
        val restored = gson.fromJson(gson.toJson(snapshot), PlatformPlaybackSnapshot::class.java)
        assertEquals("extra:qishui:scene:6", restored.additionalRadioId)
        assertEquals("专注模式", restored.fmModeLabel)
        assertTrue(restored.belongsTo(MusicPlatform.QISHUI))
        assertTrue(!restored.copy(additionalRadioId = "extra:kugou:fm").belongsTo(MusicPlatform.QISHUI))
    }
}
