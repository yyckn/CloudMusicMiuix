package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import com.hypermusic.miuix.data.model.SearchCategory

class BodianCatalogTest {
    @Test fun songConversionKeepsIdsDurationArtistsAndCover() {
        val raw = JsonParser.parseString("""{"id":228908,"songName":"晴天","artist":"周杰伦","artistId":336,"artists":[{"id":336,"name":"周杰伦"}],"album":"叶惠美","albumId":1293,"albumPic":"http://img4.kuwo.cn/a.jpg","duration":269}""").asJsonObject
        val song = BodianCatalog.parseSong(raw)
        assertEquals("bodian_228908", song.id)
        assertEquals("bodian", song.source)
        assertEquals("晴天", song.title)
        assertEquals("336", song.artistId)
        assertEquals("1293", song.albumId)
        assertEquals(269000L, song.duration)
        assertEquals("https://img4.kuwo.cn/a.jpg", song.imgUrl)
    }
    @Test fun searchResultsPreservePlaylistSourceAndCategoryMetadata() {
        val playlist = JsonParser.parseString("""{"id":95860732,"source":5,"name":"hello","creator_name":"作者","musicnum":43,"playnum":1591}""").asJsonObject
        val result = BodianCatalog.parseSearchEntity(playlist, SearchCategory.PLAYLIST)!!
        assertEquals("bodian:5:95860732", result.id)
        assertEquals(43L, result.songCount)
        assertEquals(1591L, result.playCount)
        val artist = JsonParser.parseString("""{"artistId":1627,"name":"Adele","albumNum":30,"songNum":329}""").asJsonObject
        val singer = BodianCatalog.parseSearchEntity(artist, SearchCategory.ARTIST)!!
        assertEquals("1627", singer.id)
        assertEquals(329L, singer.songCount)
        assertEquals(30L, singer.albumCount)
    }
    @Test fun albumDetailsAreMappedToSharedModel() {
        val raw = JsonParser.parseString("""{"albumInfo":{"id":520748,"name":"Hello","pic":"http://img4.kuwo.cn/cover.jpg","artist":"Hedley","artistId":27767,"musicCount":11,"showtime":"2015-11-06"}}""").asJsonObject
        val album = BodianCatalog.parseAlbum(raw)
        assertEquals(520748L, album.id)
        assertEquals("https://img4.kuwo.cn/cover.jpg", album.picUrl)
        assertEquals(11, album.size)
        assertEquals(1446768000000L, album.publishTime)
    }
    @Test fun artistInfoIncludesProfileCountsAndDescription() {
        val raw = JsonParser.parseString("""{"artistInfo":{"id":1627,"name":"Adele","pic":"http://img3.kuwo.cn/a.jpg","musicCnt":315,"albumCnt":29,"desc":"介绍"}}""").asJsonObject
        val artist = BodianCatalog.parseArtist(raw)
        assertEquals(1627L, artist.id)
        assertEquals(315, artist.musicSize)
        assertEquals(29, artist.albumSize)
        assertEquals("介绍", artist.briefDesc)
    }
}
