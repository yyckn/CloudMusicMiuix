package com.hypermusic.miuix.data.cache

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PageCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun cacheSurvivesReopeningAndSeparatesAccountsPlatformsAndPages() {
        val directory = temporary.newFolder()
        val store = PageCacheStore(directory)
        val keys = listOf("ne|A|playlist|1", "ne|B|playlist|1", "qq|A|playlist|1", "ne|A|album|1")
        keys.forEachIndexed { index, key -> store.write(key, "{\"value\":$index}") }
        val reopened = PageCacheStore(directory)
        keys.forEachIndexed { index, key -> assertEquals("{\"value\":$index}", reopened.read(key)) }
        assertNull(reopened.read("ne|C|playlist|1"))
        assertTrue(directory.listFiles()!!.all { it.name.matches(Regex("[0-9a-f]{64}\\.json")) })
    }
    @Test fun readTouchesLruAndClearDoesNotDeleteDownloadsOrOtherCacheClasses() {
        val root = temporary.newFolder()
        val audio = java.io.File(root, "downloads/song.mp3").apply { parentFile.mkdirs(); writeText("audio") }
        val images = java.io.File(root, "image_cache/cover").apply { parentFile.mkdirs(); writeText("cover") }
        val store = PageCacheStore(java.io.File(root, "page_cache"), maxFiles = 2)
        store.write("a", "A"); store.write("b", "B"); assertEquals("A", store.read("a")); store.write("c", "C")
        assertEquals("A", store.read("a")); assertNull(store.read("b")); assertEquals("C", store.read("c"))
        assertTrue(store.size() > 0)
        store.clear()
        assertEquals(0L, store.size())
        assertEquals("audio", audio.readText()); assertEquals("cover", images.readText())
    }
    @Test fun mergeMovesExistingSongsAndInsertsNewSongsWithoutKeepingDeletedRows() {
        data class Song(val id: String, val title: String)
        val b = Song("B", "B"); val d = Song("D", "D")
        val merged = reconcilePageItems(listOf(b, d), listOf(Song("A", "A"), b.copy(), Song("C", "C"), d.copy())) { it.id }
        assertEquals(listOf("A", "B", "C", "D"), merged.map { it.id })
        assertSame(b, merged[1]); assertSame(d, merged[3])
        val updated = reconcilePageItems(merged, listOf(Song("B", "New B"), d)) { it.id }
        assertEquals(listOf("New B", "D"), updated.map { it.title })
        assertSame(d, updated[1])
    }
    @Test fun repeatedSongsHaveUniqueKeysThatSurviveInsertingOtherSongs() {
        val before = stablePageItemKeys(listOf("B", "D", "B")) { it }
        val after = stablePageItemKeys(listOf("A", "B", "C", "D", "B")) { it }
        assertEquals(before[0], after[1]); assertEquals(before[1], after[3]); assertEquals(before[2], after[4])
        assertEquals(after.size, after.toSet().size)
    }
    @Test fun cacheCapacityIsBoundedAndOversizedEntriesDoNotEvictValidPages() {
        val store = PageCacheStore(temporary.newFolder(), maxBytes = 10)
        store.write("old", "123456")
        store.write("new", "abcdef")
        assertNull(store.read("old")); assertEquals("abcdef", store.read("new"))
        store.write("too-large", "x".repeat(11))
        assertNull(store.read("too-large")); assertEquals("abcdef", store.read("new"))
        assertTrue(store.size() <= 10)
    }
}
