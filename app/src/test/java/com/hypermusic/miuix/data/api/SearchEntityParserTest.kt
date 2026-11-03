package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import com.hypermusic.miuix.data.model.SearchCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchEntityParserTest {
    @Test fun qqMobileResultsNormalizeForAllCategories() {
        val root = JsonParser.parseString("""{
            "item_song":[{"mid":"song-mid"}],
            "singer":[{"singerMID":"artist-mid","singerName":"歌手","songNum":10,"albumNum":2}],
            "item_album":[{"id":123,"albummid":"album-mid","name":"专辑","singer_list":[{"name":"歌手"}],"publish_date":"2025-03-01"}],
            "item_songlist":[{"dissid":"456","dissname":"<em>歌单</em>","nickname":"作者","songnum":7,"listennum":18,"logo":"http://example.com/a"}]
        }""").asJsonObject
        val song = SearchEntityParser.normalizeQqMobile(root, 0).getAsJsonObject("song").getAsJsonArray("list")
        assertEquals("song-mid", song[0].asJsonObject.get("mid").asString)
        val artist = SearchEntityParser.parseQq(SearchEntityParser.normalizeQqMobile(root, 1), SearchCategory.ARTIST).single()
        assertEquals("artist-mid", artist.id)
        val album = SearchEntityParser.parseQq(SearchEntityParser.normalizeQqMobile(root, 2), SearchCategory.ALBUM).single()
        assertEquals("歌手", album.creator)
        assertEquals("2025-03-01", album.date)
        val playlist = SearchEntityParser.parseQq(SearchEntityParser.normalizeQqMobile(root, 3), SearchCategory.PLAYLIST).single()
        assertEquals("歌单", playlist.title)
        assertEquals("作者", playlist.creator)
        assertEquals(7L, playlist.songCount)
        assertEquals("https://example.com/a", playlist.imageUrl)
    }
    @Test fun qqPlaylistUsesNestedCreatorAndNumericCounts() {
        val root = JsonParser.parseString("""{"songlist":{"list":[{"dissid":"7039749142","dissname":"精选","creator":{"name":"作者"},"song_count":99,"listennum":417339089,"imgurl":"http://example.com/a"}]}}""").asJsonObject
        val result = SearchEntityParser.parseQq(root, SearchCategory.PLAYLIST).single()
        assertEquals("7039749142", result.id)
        assertEquals("作者", result.creator)
        assertEquals(99L, result.songCount)
        assertEquals(417339089L, result.playCount)
        assertEquals("https://example.com/a", result.imageUrl)
    }
    @Test fun qqArtistAndAlbumKeepCorrectNavigationIds() {
        val root = JsonParser.parseString("""{"singer":{"list":[{"singerMID":"abc","singerName":"歌手","songNum":1013,"albumNum":43}]},"album":{"list":[{"albumID":123,"albumMID":"xyz","albumName":"专辑","singerName":"歌手","publicTime":"2026-03-25"}]}}""").asJsonObject
        val artist = SearchEntityParser.parseQq(root, SearchCategory.ARTIST).single()
        assertEquals("abc", artist.id)
        assertEquals(1013L, artist.songCount)
        assertEquals(43L, artist.albumCount)
        val album = SearchEntityParser.parseQq(root, SearchCategory.ALBUM).single()
        assertEquals("123", album.id)
        assertEquals("2026-03-25", album.date)
        assertEquals("歌手", album.creator)
    }
    @Test fun neteaseMapsNestedCreatorAndUnknownFields() {
        val root = JsonParser.parseString("""{"playlists":[{"id":42,"name":"歌单","creator":{"nickname":"作者"},"trackCount":7,"playCount":18}],"artists":[{"id":5,"name":"歌手","musicSize":3,"albumSize":2}]}""").asJsonObject
        val list = SearchEntityParser.parseNetease(root, SearchCategory.PLAYLIST).single()
        assertEquals("作者", list.creator)
        assertEquals(7L, list.songCount)
        val artist = SearchEntityParser.parseNetease(root, SearchCategory.ARTIST).single()
        assertEquals(2L, artist.albumCount)
    }
}
