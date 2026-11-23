package com.hypermusic.miuix.history

import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.data.history.*
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class ListeningHistoryDeviceTest {
    @Test fun cloudRecordsPersistAndDeduplicatePerAccount() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        ListeningHistory.init(context)
        val prefix = "cloud-test-${UUID.randomUUID()}"
        val accountA = "$prefix-A"
        val accountB = "$prefix-B"
        val song = Track("netrack_123", "测试歌曲", "测试歌手", "", "测试专辑", "", sourceUrl = "", imgUrl = "", duration = 180_000)
        val first = CloudListeningEvent(song, System.currentTimeMillis())
        try {
            ListeningHistory.saveCloud(accountA, listOf(first, first))
            ListeningHistory.saveCloud(accountA, listOf(first.copy(track = song.copy(title = "更新后的歌名"))))
            ListeningHistory.saveCloud(accountB, listOf(first))
            assertEquals(1, ListeningHistory.cloudRecords(accountA).size)
            assertEquals("更新后的歌名", ListeningHistory.cloudRecords(accountA).single().track.title)
            assertEquals("测试歌曲", ListeningHistory.cloudRecords(accountB).single().track.title)
            assertFalse(ListeningHistory.records().any { it.accountId.startsWith(prefix) })
            // Cloud history is separate from the upload queue and from disposable page caches.
            assertTrue(context.getDatabasePath("listening_history.db").exists())
        } finally {
            android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("listening_history.db").path, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.delete("cloud_plays", "account IN (?, ?)", arrayOf(accountA, accountB))
            }
        }
    }

    @Test fun durableRecordsKeepMetadataAndRetryOnlyOncePerProcess() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        ListeningHistory.init(context)
        val id = UUID.randomUUID().toString()
        val calls = AtomicInteger()
        val oldTransport = ListeningHistory.reportTransport
        val song = Track("history-test-$id", "本地听歌记录测试", "测试歌手", "", "测试专辑", "", sourceUrl = "", imgUrl = "", localDownloadId = "local-test")
        var uploadedAccount: String? = null
        ListeningHistory.reportTransport = { track, account, duration, started ->
            if (track.id == song.id) {
                uploadedAccount = account
                assertEquals(31_000L, duration); assertTrue(started > 0)
                calls.incrementAndGet()
            }
            Result.failure(Exception("测试：离线"))
        }
        try {
            val record = ListeningRecord(id, song, "pinned-playback-account", System.currentTimeMillis(), 31_000, 180_000, true, false, false, null, mapOf("2026-11-22" to 31_000L))
            ListeningHistory.save(record)
            ListeningHistory.awaitWrites()
            assertEquals(0, calls.get()) // active sessions must not upload partial totals
            val read = ListeningHistory.records().first { it.id == id }
            assertEquals(song, read.track); assertEquals(record.dayTotals, read.dayTotals)
            assertEquals("pinned-playback-account", read.accountId)
            ListeningHistory.save(record.copy(finished = true))
            ListeningHistory.awaitWrites()
            withTimeout(5_000) { while (ListeningHistory.records().first { it.id == id }.error == null) delay(25) }
            assertEquals(1, calls.get()); assertEquals("pinned-playback-account", uploadedAccount)
            repeat(3) { ListeningHistory.retryAtLaunch() }
            ListeningHistory.awaitWrites(); delay(200)
            assertEquals(1, calls.get())
            assertFalse(ListeningHistory.records().first { it.id == id }.uploaded)
            assertTrue(context.getDatabasePath("listening_history.db").exists())
            assertFalse(context.getDatabasePath("listening_history.db").startsWith(context.cacheDir))
        } finally {
            ListeningHistory.deleteForTest(listOf(id))
            ListeningHistory.reportTransport = oldTransport
        }
    }
}
