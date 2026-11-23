package com.hypermusic.miuix.history

import android.content.*
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.data.history.ListeningHistory
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.service.MediaPlaybackService
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ListeningPlaybackDeviceTest {
    @Test fun backgroundLocalPlaybackCountsActualAudioAndExcludesPause() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ComponentActivity
        val connected = CountDownLatch(1)
        lateinit var service: MediaPlaybackService
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { service = (binder as MediaPlaybackService.LocalBinder).getService(); connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val count = 16_000 * 60
        val file = context.cacheDir.resolve("listening-test-${UUID.randomUUID()}.wav")
        file.writeBytes(java.nio.ByteBuffer.allocate(44 + count * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + count * 2); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(16_000); putInt(32_000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(count * 2)
        }.array())
        val track = Track("netrack_history_${UUID.randomUUID()}", "本地音频计时测试", "测试", "", "测试", "", sourceUrl = "", imgUrl = "", duration = 60_000, localDownloadId = "test-local")
        val reportedMs = AtomicLong()
        val transport = ListeningHistory.reportTransport
        ListeningHistory.reportTransport = { song, _, ms, _ -> if (song.id == track.id) reportedMs.set(ms); Result.success(Unit) }
        assertTrue(context.bindService(Intent(context, MediaPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { service.clearPlayback(); service.restoreTrackWithUrl(track, file.toURI().toString(), 0, true) }
            delay(12_500)
            val first = ListeningHistory.records().first { it.track.id == track.id }
            assertTrue(first.listenedMs > 10_000); assertFalse(first.finished)
            instrumentation.runOnMainSync { service.togglePlayPause() }
            delay(4_000)
            instrumentation.runOnMainSync { service.togglePlayPause() }
            delay(21_000)
            instrumentation.runOnMainSync { service.clearPlayback() }
            ListeningHistory.awaitWrites()
            withTimeout(5_000) { while (reportedMs.get() == 0L) delay(25) }
            val record = ListeningHistory.records().first { it.track.id == track.id }
            assertTrue(record.finished); assertTrue(record.eligible)
            assertTrue("Pause must not add 4 seconds to actual listening", record.listenedMs in 31_000L..35_000L)
            assertEquals(record.listenedMs, record.dayTotals.values.sum())
            assertNotNull(record.track.localDownloadId)
        } finally {
            instrumentation.runOnMainSync { service.clearPlayback(); activity.finish() }
            context.unbindService(connection)
            ListeningHistory.awaitWrites()
            ListeningHistory.deleteForTest(ListeningHistory.records().filter { it.track.id == track.id }.map { it.id })
            ListeningHistory.reportTransport = transport
            file.delete()
        }
    }
}
