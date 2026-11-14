package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class KugouNativeLyricsTest {
    private fun song(id: String, source: String = "kugou", hash: String = "") = Track(id, "歌曲", "歌手", "", "", "", source, "", "", kugouHash = hash)

    @Test fun usesActualHashInsteadOfAlbumAudioId() {
        val hash = "1f7e7659b9fff4fb6f71562a61bbc25f"
        assertEquals(hash.uppercase(), KugouLyricsApi.nativeHash(song("kugou_32029513", hash = hash)))
        assertEquals(hash.uppercase(), KugouLyricsApi.nativeHash(song("kgtrack_$hash")))
        assertEquals(hash.uppercase(), KugouLyricsApi.nativeHash(song("kugou_$hash")))
        assertNull(KugouLyricsApi.nativeHash(song("kugou_32029513")))
        assertNull(KugouLyricsApi.nativeHash(song("kgtrack_$hash", source = "netease")))
    }
}
