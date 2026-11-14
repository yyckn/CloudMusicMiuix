package com.hypermusic.miuix.data.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioCacheKeyTest {
    @Test fun identicalIdsAndFormatsRemainDistinctAcrossPlatforms() {
        val sources = listOf("netease", "qqmusic", "bodian", "kugou", "qishui")
        val keys = sources.map { AudioCache.key(it, "123", "guest:standard") }
        assertEquals(sources.size, keys.toSet().size)
    }

    @Test fun accountAndActualFormatArePartOfTheCacheKey() {
        val formats = listOf("account-a:128000:mp3", "account-b:128000:mp3", "account-a:320000:mp3", "account-a:0:flac")
        assertEquals(formats.size, formats.map { AudioCache.key("kugou", "123", it) }.toSet().size)
    }
}
