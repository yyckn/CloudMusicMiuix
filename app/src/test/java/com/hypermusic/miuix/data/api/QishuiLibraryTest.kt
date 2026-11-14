package com.hypermusic.miuix.data.api

import org.junit.Assert.*
import org.junit.Test
import com.google.gson.JsonParser

class QishuiLibraryTest {
    @Test fun mixedCollectionResponseIsFilteredByActualType() {
        val data = JsonParser.parseString("""{"has_more":false,"mixed_collections":[{"playlist":{"base_info":{"id":"12"}}},{"album":{"base_info":{"id":"34"}}}]}""").asJsonObject
        val rows = data.getAsJsonArray("mixed_collections")
        assertEquals("12", QishuiLibrary.parsePage(data, rows, "", false).items.single().id)
        assertEquals(34L, QishuiLibrary.parseAlbumPage(data, rows, "").items.single().id)
    }
    @Test fun parsesCollectedAlbumAndCursor() {
        val data = JsonParser.parseString("""{"has_more":true,"next_cursor":"next","mixed_collections":[{"album":{"base_info":{"id":"123","name":"专辑","count_tracks":4,"artists":[{"id":"456","name":"歌手"}]}}}]}""").asJsonObject
        val page = QishuiLibrary.parseAlbumPage(data, data.getAsJsonArray("mixed_collections"), "")
        assertEquals(123L, page.items.single().id)
        assertEquals("歌手", page.items.single().artist)
        assertTrue(page.hasMore)
        assertTrue(runCatching { QishuiLibrary.parseAlbumPage(data, data.getAsJsonArray("mixed_collections"), "next") }.isFailure)
        val unsupported = JsonParser.parseString("""{"mixed_collections":[{"unknown":{}}]}""").asJsonObject
        assertTrue(runCatching { QishuiLibrary.parseAlbumPage(unsupported, unsupported.getAsJsonArray("mixed_collections"), "") }.isFailure)
        val mixed = JsonParser.parseString("""{"mixed_collections":[{"album":{"base_info":{"id":"123"}}},{"unknown":{}}]}""").asJsonObject
        assertTrue(runCatching { QishuiLibrary.parseAlbumPage(mixed, mixed.getAsJsonArray("mixed_collections"), "") }.isFailure)
    }
    @Test fun findsNestedLikedTrackWithoutParsingUnrelatedSongs() {
        val rows = JsonParser.parseString("""[null,{"entity":{"media":{"track_entity":{"base_info":{"id":"7351234567890123456"}}}}},{"track":{"id":"42"}}]""").asJsonArray
        assertTrue(QishuiLibrary.containsTrack(rows, "7351234567890123456"))
        assertTrue(QishuiLibrary.containsTrack(rows, "42"))
        assertTrue(runCatching { QishuiLibrary.containsTrack(rows, "7") }.isFailure)
        assertFalse(QishuiLibrary.containsTrack(JsonParser.parseString("""[{"media_id":"42"}]""").asJsonArray, "7"))
        assertTrue(QishuiLibrary.containsTrack(JsonParser.parseString("""[{"media_id":"42"}]""").asJsonArray, "42"))
    }
    @Test fun mediaKeepsLargeIdsAsStringsAndRemovesDuplicates() {
        val result = QishuiLibrary.media(listOf("7351234567890123456", "7351234567890123456", "42"))
        assertEquals(2, result.size())
        assertEquals("7351234567890123456", result[0].asJsonObject["id"].asString)
        assertEquals("track", result[0].asJsonObject["type"].asString)
    }

    @Test fun invalidOrOversizedMediaListsAreRejected() {
        assertTrue(runCatching { QishuiLibrary.media(emptyList()) }.isFailure)
        assertTrue(runCatching { QishuiLibrary.media(listOf("../1")) }.isFailure)
        assertTrue(runCatching { QishuiLibrary.media(List(101) { "1" }) }.isFailure)
    }

    @Test fun parsesOwnedAndCollectedPlaylistsAndRejectsStalledCursor() {
        val data = JsonParser.parseString("""{"has_more":true,"next_cursor":"next","playlists":[{"playlist":{"base_info":{"id":"7351234567890123456","owner_id":"123","type":1}}}]}""").asJsonObject
        val page = QishuiLibrary.parsePage(data, data.getAsJsonArray("playlists"), "", true)
        assertEquals("7351234567890123456", page.items.single().id)
        assertTrue(page.items.single().liked)
        assertTrue(page.items.single().owned)
        assertTrue(page.hasMore)
        assertFalse(QishuiLibrary.parsePage(data, data.getAsJsonArray("playlists"), "", false).items.single().owned)
        assertTrue(runCatching { QishuiLibrary.parsePage(data, data.getAsJsonArray("playlists"), "next", true) }.isFailure)
    }

    @Test fun rejectsUnrecognizedPlaylistRowsInsteadOfReportingAnEmptyLibrary() {
        val data = JsonParser.parseString("""{"mixed_collections":[{"unexpected":{}}]}""").asJsonObject
        assertTrue(runCatching { QishuiLibrary.parsePage(data, data.getAsJsonArray("mixed_collections"), "", false) }.isFailure)
        val empty = JsonParser.parseString("""{"mixed_collections":[]}""").asJsonObject
        assertTrue(QishuiLibrary.parsePage(empty, empty.getAsJsonArray("mixed_collections"), "", false).items.isEmpty())
        val mixed = JsonParser.parseString("""{"mixed_collections":[{"playlist":{"base_info":{"id":"123"}}},{"unexpected":{}}]}""").asJsonObject
        assertTrue(runCatching { QishuiLibrary.parsePage(mixed, mixed.getAsJsonArray("mixed_collections"), "", false) }.isFailure)
    }
}
