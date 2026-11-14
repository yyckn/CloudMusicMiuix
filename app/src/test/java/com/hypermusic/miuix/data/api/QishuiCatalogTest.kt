package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class QishuiCatalogTest {
    @Test fun searchUsesServerCursorsAndKeepsUnconsumedRows() = kotlinx.coroutines.runBlocking {
        val calls = mutableListOf<Int>()
        suspend fun page(offset: Int) = QishuiCatalog.readSearchPages("track", offset, 2, { it["id"].asString }) { cursor ->
            calls += cursor
            JsonParser.parseString(if (cursor == 0)
                """{"result_groups":[{"id":"tracks","data":[{"entity":{"track":{"id":"1"}}},{"entity":{"track":{"id":"2"}}},{"entity":{"track":{"id":"3"}}}],"has_more":true,"next_cursor":"50"}]}"""
            else """{"result_groups":[{"id":"tracks","data":[{"entity":{"track":{"id":"4"}}}],"has_more":false}]}""").asJsonObject
        }
        val first = page(0)
        val second = page(first.nextOffset)
        assertEquals(listOf("1", "2"), first.items)
        assertEquals(listOf("3", "4"), second.items)
        assertEquals(listOf(0, 0, 50), calls)
        org.junit.Assert.assertFalse(second.hasMore)
    }

    @Test fun searchRejectsStalledServerCursor() = kotlinx.coroutines.runBlocking {
        val data = JsonParser.parseString("""{"result_groups":[{"id":"tracks","data":[{"entity":{"track":{"id":"1"}}}],"has_more":true,"next_cursor":"0"}]}""").asJsonObject
        org.junit.Assert.assertTrue(runCatching { QishuiCatalog.readSearchPages("track", 0, 2, { it }) { data } }.isFailure)
    }
    @Test fun malformedSearchRowsAreNotReportedAsAnEmptyResult() = kotlinx.coroutines.runBlocking {
        val data = JsonParser.parseString("""{"result_groups":[{"id":"tracks","data":[{"unexpected":{}}],"has_more":false}]}""").asJsonObject
        org.junit.Assert.assertTrue(runCatching { QishuiCatalog.readSearchPages("track", 0, 2, { it }) { data } }.isFailure)
        val empty = JsonParser.parseString("""{"result_groups":[{"id":"tracks","data":[],"has_more":false}]}""").asJsonObject
        org.junit.Assert.assertTrue(QishuiCatalog.readSearchPages("track", 0, 2, { it }) { empty }.items.isEmpty())
    }
    @Test fun playlistReadsEachCursorOnceAndPreservesOrder() = kotlinx.coroutines.runBlocking {
        val cursors = mutableListOf<String>()
        val result = QishuiCatalog.readPlaylistPages("123", "456", 0, 200) { body ->
            val cursor = body["cursor"].asString
            cursors += cursor
            JsonParser.parseString(if (cursor == "0")
                """{"playlist":{"base_info":{"id":"123"}},"media_entities":[{"track_entity":{"base_info":{"id":"11","name":"A"}}}],"has_more":true,"next_cursor":"next"}"""
            else """{"media_entities":[{"track_entity":{"base_info":{"id":"12","name":"B"}}}],"has_more":false}""").asJsonObject
        }
        assertEquals(listOf("0", "next"), cursors)
        assertEquals(listOf("qishui_11", "qishui_12"), result.songs.items.map { it.id })
        org.junit.Assert.assertFalse(result.songs.hasMore)
    }

    @Test fun playlistRejectsARepeatedCursor() = kotlinx.coroutines.runBlocking {
        val response = JsonParser.parseString("""{"media_entities":[{"track_entity":{"base_info":{"id":"11"}}}],"has_more":true,"next_cursor":"0"}""").asJsonObject
        org.junit.Assert.assertTrue(runCatching {
            QishuiCatalog.readPlaylistPages("123", "", 0, 200) { response }
        }.isFailure)
    }
    @Test fun artistProfileRetainsNameAndAvatarForLargeIds() {
        val json = com.google.gson.JsonParser.parseString("""{"base_info":{"id":"98765432109876543210","name":"歌手","desc":"介绍","url_avatar":{"urls":["https://example.com/a.jpg"]}},"resource_cnt":{"track_cnt":37}}""").asJsonObject
        val artist = QishuiCatalog.parseArtist(json)
        org.junit.Assert.assertEquals("歌手", artist.name)
        org.junit.Assert.assertEquals("https://example.com/a.jpg", artist.picUrl)
        org.junit.Assert.assertEquals(37, artist.musicSize)
    }
    @Test fun searchedArtistUsesBaseInfoCountsAndAvatar() {
        val json = com.google.gson.JsonParser.parseString("""{"base_info":{"id":"6681166129722824706","name":"周杰伦","count_tracks":22,"count_albums":0,"url_avatar":{"urls":["https://example.com/avatar.jpg"]}}}""").asJsonObject
        val result = QishuiCatalog.artistEntity(json)!!
        org.junit.Assert.assertEquals("6681166129722824706", result.id)
        org.junit.Assert.assertEquals(22L, result.songCount)
        org.junit.Assert.assertEquals("https://example.com/avatar.jpg", result.imageUrl)
        org.junit.Assert.assertEquals(22, QishuiCatalog.parseArtist(json).musicSize)
    }
    @Test fun playlistEntityPreservesOwnerCoverAndTrackCount() {
        val json = com.google.gson.JsonParser.parseString("""{"base_info":{"id":"7351234567890123456","title":"歌单","owner_id":"456"},"display_info":{"url_cover":{"urls":["https://example.com/cover.jpg"]}},"resource_cnt":{"track_cnt":12},"relation_info":{"owner":{"nickname":"作者"}}}""").asJsonObject
        val entity = QishuiCatalog.playlistEntity(json)!!
        org.junit.Assert.assertEquals("qishui:7351234567890123456:456", entity.id)
        org.junit.Assert.assertEquals("歌单", entity.title)
        org.junit.Assert.assertEquals("https://example.com/cover.jpg", entity.imageUrl)
        org.junit.Assert.assertEquals(12L, entity.songCount)
        org.junit.Assert.assertEquals("作者", entity.creator)
    }
    @Test fun preservesLargeIdentifiersAndCover() {
        val item = JsonParser.parseString("""{"base_info":{"id":"7421234567890123456","name":"歌曲","duration_ms":123456},"related_info":{"artist_links":[{"id":"7771234567890123456","name":"歌手"}],"album_link":{"id":"9991234567890123456","name":"专辑"}},"display_info":{"cover_url":{"urls":["https://example.com/"],"uri":"abc","template_prefix":"tpl"}}}""").asJsonObject
        val song = QishuiCatalog.parseTrack(item)
        assertEquals("qishui_7421234567890123456", song.id)
        assertEquals("https://www.qishui.com/track/7421234567890123456", song.sourceUrl)
        assertEquals("7771234567890123456", song.artistId)
        assertEquals("9991234567890123456", song.albumId)
        assertEquals(123456L, song.duration)
        assertEquals("https://example.com/abc~tpl-image.image", song.imgUrl)
    }

    @Test fun unwrapsNestedPlaylistTrack() {
        val raw = JsonParser.parseString("""{"entity":{"media":{"track_entity":{"base_info":{"id":"123","name":"歌曲"}}}}}""").asJsonObject
        assertEquals("qishui_123", QishuiCatalog.parseTrack(QishuiCatalog.unwrapTrack(raw)).id)
    }
}
