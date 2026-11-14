package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BodianLibraryTest {
    @Test fun unrecognizedCollectionsCannotBeMistakenForAnEmptyLibrary() {
        val response = JsonParser.parseString("""{"total":1,"playLists":[{"name":"未知条目"}]}""")
        assertThrows(IllegalStateException::class.java) {
            BodianLibrary.parseCollectionsPage(response, 0, 50, false)
        }
    }
    @Test fun mixedCollectionsAdvanceEvenWhenFilteredPageIsEmpty() {
        val first = JsonParser.parseString("""{"total":2,"playLists":[{"id":11,"sourceType":4,"name":"歌单"}]}""")
        val second = JsonParser.parseString("""{"total":2,"playLists":[{"id":22,"albumId":33,"sourceType":6,"name":"专辑"}]}""")
        val albums = BodianLibrary.parseCollectionsPage(first, 0, 1, true)
        assertEquals(emptyList<BodianLibrary.Collection>(), albums.items)
        assertEquals(true, albums.hasMore)
        assertEquals(1, albums.nextOffset)
        val next = BodianLibrary.parseCollectionsPage(second, albums.nextOffset, 1, true)
        assertEquals("33", next.items.single().id)
        assertEquals(false, next.hasMore)
        assertEquals(emptyList<BodianLibrary.Collection>(), BodianLibrary.parseCollectionsPage(second, 1, 1, false).items)
    }
    @Test fun preservesCollectionTypeAndSource() {
        val album = JsonParser.parseString("""{"id":42,"albumId":88,"sourceType":6,"name":"专辑","pic":"http://img4.kuwo.cn/a.jpg","musicCnt":12}""").asJsonObject
        val result = BodianLibrary.parseCollection(album)!!
        assertEquals("88", result.id)
        assertEquals(true, result.isAlbum)
        assertEquals(6, result.source)
        assertEquals(12, result.count)
        assertEquals("https://img4.kuwo.cn/a.jpg", result.cover)
        assertNull(BodianLibrary.parseCollection(JsonParser.parseString("""{"id":"../../x"}""").asJsonObject))
    }
}
