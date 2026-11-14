package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class QishuiArtistWorksTest {
    @Test fun artistProfileRequiresMatchingId() {
        val root = JsonParser.parseString("""{"artist_info":{"id":"123","name":"歌手","count_tracks":10,"count_albums":4,"artist_profile":{"intro":"介绍"}}}""").asJsonObject
        val artist = QishuiArtistWorks.parseArtist(root, "123")
        assertEquals("歌手", artist.name)
        assertEquals(10, artist.musicSize)
        assertEquals("介绍", artist.briefDesc)
        assertTrue(runCatching { QishuiArtistWorks.parseArtist(root, "999") }.isFailure)
    }
    @Test fun songPageUsesServerCursorAndRetainsAlbum() {
        val root = JsonParser.parseString("""{"tracks":[{"id":"123","name":"歌","duration":120000,"album":{"id":"456","name":"专辑"},"artists":[{"id":"789","name":"歌手"}]}],"has_more":true,"next_cursor":"51"}""").asJsonObject
        val page = QishuiArtistWorks.parseSongs(root, "")
        assertEquals("51", page.cursor)
        assertTrue(page.more)
        assertEquals("456", page.items.single().albumId)
        assertEquals("789", page.items.single().artistId)
        assertTrue(runCatching { QishuiArtistWorks.parseSongs(root, "51") }.isFailure)
    }
    @Test fun albumPageParsesReturnedItems() {
        val root = JsonParser.parseString("""{"albums":[{"id":"456","name":"专辑","count_tracks":4,"artists":[{"id":"789","name":"歌手"}]}],"has_more":false}""").asJsonObject
        val page = QishuiArtistWorks.parseAlbums(root, "")
        assertEquals(456L, page.items.single().id)
        assertEquals("歌手", page.items.single().artist)
        assertFalse(page.more)
    }
    @Test fun invalidSongAndAlbumResponsesAreNotReportedAsEmptyPages() {
        val missing = JsonParser.parseString("""{"status_code":0}""").asJsonObject
        val invalidSongs = JsonParser.parseString("""{"tracks":[{"name":"歌"}],"has_more":false}""").asJsonObject
        val invalidAlbums = JsonParser.parseString("""{"albums":[{"name":"专辑"}],"has_more":false}""").asJsonObject
        assertTrue(runCatching { QishuiArtistWorks.parseSongs(missing, "") }.isFailure)
        assertTrue(runCatching { QishuiArtistWorks.parseAlbums(missing, "") }.isFailure)
        assertTrue(runCatching { QishuiArtistWorks.parseSongs(invalidSongs, "") }.isFailure)
        assertTrue(runCatching { QishuiArtistWorks.parseAlbums(invalidAlbums, "") }.isFailure)
        val empty = JsonParser.parseString("""{"tracks":[],"has_more":false}""").asJsonObject
        assertTrue(QishuiArtistWorks.parseSongs(empty, "").items.isEmpty())
    }
}
