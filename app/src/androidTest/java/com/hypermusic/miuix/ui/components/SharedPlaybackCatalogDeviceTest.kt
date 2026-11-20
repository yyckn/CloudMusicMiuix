package com.hypermusic.miuix.ui.components

import com.hypermusic.miuix.data.api.*
import kotlinx.coroutines.runBlocking
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPlaybackCatalogDeviceTest {
    @Test fun realCatalogsMatchBothDirectionsUsingIndependentSongIds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        QqMusicApi.init(context)
        val results = QqMusicApi.searchSongs("晴天").getOrThrow()
        val expected = com.hypermusic.miuix.data.model.Track("", "晴天", "周杰伦", "", "", "", sourceUrl = "", imgUrl = "")
        val qq = results
            .firstOrNull { SharedPlaybackAccounts.sameSong(expected, it) }
            ?: error("QQ目录没有返回预期的测试歌曲：${results.take(8).map { it.title + " / " + it.artist }}")
        val bodian = SharedPlaybackAccounts.match(context, qq, "bodian")
        assertEquals("bodian", bodian.source)
        assertNotEquals(qq.id, bodian.id)
        assertTrue(SharedPlaybackAccounts.sameSong(qq, bodian))
        val roundTrip = SharedPlaybackAccounts.match(context, bodian, "qqmusic")
        assertEquals("qqmusic", roundTrip.source)
        assertTrue(SharedPlaybackAccounts.sameSong(bodian, roundTrip))
    }
}
