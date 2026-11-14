package com.hypermusic.miuix.data.api

import org.junit.Assert.*
import org.junit.Test

class MusicPlatformTest {
    @Test fun platformKeysAndTrackSourcesRemainDistinct() {
        val platforms = MusicPlatform.entries
        assertEquals(platforms.size, platforms.map { it.storageKey }.distinct().size)
        assertEquals(platforms.size, platforms.map { it.trackSource }.distinct().size)
        platforms.forEach {
            assertEquals(it, MusicPlatform.fromStorageKey(it.storageKey))
            assertEquals(it, MusicPlatform.fromTrackSource(it.trackSource))
        }
        assertEquals(MusicPlatform.QQ, MusicPlatform.fromStorageKey("qq"))
        assertEquals(MusicPlatform.QQ, MusicPlatform.fromTrackSource("qqmusic"))
        assertNull(MusicPlatform.fromStorageKey("unknown"))
    }
}
