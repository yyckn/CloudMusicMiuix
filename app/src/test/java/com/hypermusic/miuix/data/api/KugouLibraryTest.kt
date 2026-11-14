package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class KugouLibraryTest {
    @Test fun parsesPersonalAndCollectedEntriesWithoutConfusingIds() {
        val response = JsonParser.parseString("""{"data":{"total":2,"lists":[{"listid":2,"global_collection_id":"abc","name":"我喜欢","type":0,"count":20},{"listid":9,"source":2,"musiclib_id":"42","name":"专辑","type":1}]}}""").asJsonObject
        val page = KugouLibrary.parsePage(response, 1, 100)
        assertEquals(2, page.items.size)
        assertTrue(page.items[0].liked)
        assertTrue(page.items[0].owned)
        assertEquals("abc", page.items[0].globalId)
        assertTrue(page.items[1].album)
        assertFalse(page.items[1].owned)
        assertNull(page.nextPage)
    }
    @Test fun missingListIsNotMistakenForEmptyLibrary() {
        val response = JsonParser.parseString("""{"data":{}}""").asJsonObject
        assertTrue(runCatching { KugouLibrary.parsePage(response, 1, 30) }.isFailure)
    }
    @Test fun zeroMusicLibraryIdDoesNotTurnLikedPlaylistIntoAlbum() {
        val row = JsonParser.parseString("""{"listid":2,"global_collection_id":"collection_1","name":"我喜欢","source":1,"musiclib_id":0}""").asJsonObject
        val liked = KugouLibrary.parseCollection(row)!!
        assertTrue(liked.liked)
        assertFalse(liked.album)
    }
}
