package com.hypermusic.miuix.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackPlatformDataTest {
    private val gson = Gson()

    @Test
    fun legacyJsonRemainsReadable() {
        val json = """{"id":"song","title":"Song","artist":"Artist","artistId":"","album":"Album","albumId":"","source":"kugou","sourceUrl":"","imgUrl":"","isCloudDisk":true,"qqMediaMid":"mid","qqSongId":42,"bodianFreeSign":"sign","kugouHash":"hash","kugouAlbumAudioId":"audio"}"""
        val track = gson.fromJson(json, Track::class.java)

        assertEquals(true, track.platform.isCloudDisk)
        assertEquals("mid", track.platform.qqMediaMid)
        assertEquals(42L, track.platform.qqSongId)
        assertEquals("sign", track.platform.bodianFreeSign)
        assertEquals("hash", track.platform.kugouHash)
        assertEquals("audio", track.platform.kugouAlbumAudioId)
    }

    @Test
    fun structuredPlatformDataRoundTripsWithoutLegacyFields() {
        val original = Track(
            id = "song", title = "Song", artist = "Artist", artistId = "",
            album = "Album", albumId = "", source = "qqmusic", sourceUrl = "", imgUrl = "",
            platformData = TrackPlatformData(qqMediaMid = "mid", qqSongId = 42)
        )

        val restored = gson.fromJson(gson.toJson(original), Track::class.java)

        assertEquals(original, restored)
        assertEquals("mid", restored.platform.qqMediaMid)
        assertEquals(42L, restored.platform.qqSongId)
    }
}
