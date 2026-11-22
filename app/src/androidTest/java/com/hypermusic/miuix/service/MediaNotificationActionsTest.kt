package com.hypermusic.miuix.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MediaNotificationActionsTest {
    private lateinit var dismissalPrefs: android.content.SharedPreferences
    private var hadDismissal = false
    private var dismissalValue = false
    @org.junit.Before fun preserveDismissalPreference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(context.packageName.endsWith(".glasstest"))
        dismissalPrefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        hadDismissal = dismissalPrefs.contains(MediaPlaybackService.PREF_MEDIA_DISMISSED)
        dismissalValue = dismissalPrefs.getBoolean(MediaPlaybackService.PREF_MEDIA_DISMISSED, false)
    }
    @org.junit.After fun restoreDismissalPreference() {
        if (::dismissalPrefs.isInitialized) dismissalPrefs.edit().apply {
            if (hadDismissal) putBoolean(MediaPlaybackService.PREF_MEDIA_DISMISSED, dismissalValue)
            else remove(MediaPlaybackService.PREF_MEDIA_DISMISSED)
        }.commit()
    }
    @Test fun fallbackClosePreservesAllPlatformSnapshotsAndPlaybackRecords() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue(context.packageName.endsWith(".glasstest"))
        val connected = CountDownLatch(1)
        lateinit var service: MediaPlaybackService
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as MediaPlaybackService.LocalBinder).getService()
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        val snapshotKeys = listOf("netease", "qq", "bodian", "kugou", "qishui").map { "playback_snapshot_$it" }
        val keys = snapshotKeys + listOf("last_track", "last_playlist", "play_mode_type", "intelligence_song_id", "intelligence_playlist_id")
        val saved = keys.associateWith { prefs.getString(it, null) }
        assertTrue(context.bindService(Intent(context, MediaPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val trackField = MediaPlaybackService::class.java.getDeclaredField("currentTrack").apply { isAccessible = true }
            val close = MediaPlaybackService::class.java.getDeclaredMethod("closeFromNotification").apply { isAccessible = true }
            for (source in listOf("bodian", "kugou", "qishui")) {
                prefs.edit().apply {
                    snapshotKeys.forEach { putString(it, "saved") }
                    putString("last_track", "{\"source\":\"netease\"}")
                    putString("last_playlist", "netease-list")
                    putString("play_mode_type", "intelligence")
                }.commit()
                instrumentation.runOnMainSync {
                    service.onNotificationClose = null
                    trackField.set(service, Track(id = "test", title = "Test", artist = "Test", artistId = "", album = "", albumId = "", source = source, sourceUrl = "", imgUrl = ""))
                    close.invoke(service)
                }
                snapshotKeys.forEach { assertEquals("saved", prefs.getString(it, null)) }
                assertEquals("test", service.getCurrentTrack()?.id)
                assertEquals("netease-list", prefs.getString("last_playlist", null))
                assertEquals("intelligence", prefs.getString("play_mode_type", null))
            }
        } finally {
            prefs.edit().apply { saved.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) } }.commit()
            context.unbindService(connection)
        }
    }

    @Test fun mediaSessionDispatchesFavoriteAndClose() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue(context.packageName.endsWith(".glasstest"))
        val connected = CountDownLatch(1)
        lateinit var service: MediaPlaybackService
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as MediaPlaybackService.LocalBinder).getService()
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        assertTrue(context.bindService(Intent(context, MediaPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val track = Track(id = "test", title = "Test", artist = "Test", artistId = "", album = "", albumId = "", sourceUrl = "", imgUrl = "")
            val favorite = CountDownLatch(1)
            val closed = CountDownLatch(1)
            val sessionField = MediaPlaybackService::class.java.getDeclaredField("mediaSession").apply { isAccessible = true }
            val trackField = MediaPlaybackService::class.java.getDeclaredField("currentTrack").apply { isAccessible = true }
            val playerField = MediaPlaybackService::class.java.getDeclaredField("player").apply { isAccessible = true }
            val createPlayer = MediaPlaybackService::class.java.getDeclaredMethod("createPlayer", Track::class.java).apply { isAccessible = true }
            val update = MediaPlaybackService::class.java.getDeclaredMethod("updatePlaybackState", Int::class.javaPrimitiveType).apply { isAccessible = true }
            lateinit var session: MediaSessionCompat
            instrumentation.runOnMainSync {
                service.clearPlayback()
                trackField.set(service, track)
                MediaPlaybackService::class.java.getDeclaredField("currentUrl").apply { isAccessible = true }.set(service, "file:///test.wav")
                playerField.set(service, createPlayer.invoke(service, track))
                service.onNotificationFavorite = { if (it == track) favorite.countDown() }
                service.onNotificationClose = { closed.countDown() }
                update.invoke(service, PlaybackStateCompat.STATE_PAUSED)
                session = sessionField.get(service) as MediaSessionCompat
                session.isActive = true
            }
            val actions = session.controller.playbackState.customActions.map { it.action }
            assertTrue(actions.contains(MediaPlaybackService.ACTION_CLOSE))
            assertTrue(actions.contains(MediaPlaybackService.ACTION_FAVORITE))
            session.controller.transportControls.sendCustomAction(MediaPlaybackService.ACTION_FAVORITE, null)
            assertTrue(favorite.await(10, TimeUnit.SECONDS))
            session.controller.transportControls.sendCustomAction(MediaPlaybackService.ACTION_CLOSE, null)
            assertTrue(closed.await(10, TimeUnit.SECONDS))
            assertEquals(track, trackField.get(service))
            assertNull(playerField.get(service))
            assertFalse(session.isActive)
        } finally {
            context.unbindService(connection)
        }
    }

    @Test fun closeReleasesTheEngineAndResumeReregistersMediaAtTheSamePosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, androidx.activity.ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as androidx.activity.ComponentActivity
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val connected = CountDownLatch(1)
        lateinit var service: MediaPlaybackService
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as MediaPlaybackService.LocalBinder).getService(); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val count = 16_000 * 30
        val wav = java.nio.ByteBuffer.allocate(44 + count * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + count * 2); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(16_000); putInt(32_000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(count * 2)
        }.array()
        val file = context.cacheDir.resolve("notification-close-${java.util.UUID.randomUUID()}.wav").apply { writeBytes(wav) }
        assertTrue(context.bindService(Intent(context, MediaPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val track = Track("notification-test", "媒体关闭测试", "测试", "", "测试", "", source = "offline-test", sourceUrl = "", imgUrl = "", duration = 30_000)
            val queue = listOf(track, track.copy(id = "next", title = "下一首"))
            val type = MediaPlaybackService::class.java
            val session = type.getDeclaredField("mediaSession").apply { isAccessible = true }.get(service) as MediaSessionCompat
            val close = type.getDeclaredMethod("closeFromNotification").apply { isAccessible = true }
            val player = type.getDeclaredField("player").apply { isAccessible = true }
            val playlist = type.getDeclaredField("playlist").apply { isAccessible = true }
            instrumentation.runOnMainSync {
                service.clearPlayback(); service.onNotificationClose = null
                service.setPlaylist(queue)
                service.restoreTrackWithUrl(track, file.toURI().toString(), 4_200, true, "notification-test")
            }
            Thread.sleep(1200)
            var savedPosition = 0L
            instrumentation.runOnMainSync {
                assertTrue(service.isPlaying()); assertTrue(session.isActive)
                assertTrue(service.getCurrentPosition() >= 4_200)
                close.invoke(service)
                savedPosition = service.getCurrentPosition()
                assertFalse(service.isPlaying()); assertFalse(session.isActive)
                assertTrue(service.mediaNotificationDismissed)
                assertTrue(dismissalPrefs.getBoolean(MediaPlaybackService.PREF_MEDIA_DISMISSED, false))
                assertNull(player.get(service))
                assertEquals(track, service.getCurrentTrack()); assertEquals(queue, playlist.get(service))
                // A delayed favorite / artwork update must not recreate a closed notification.
                service.setFavoriteState(track, true)
                type.getDeclaredMethod("showNotification").apply { isAccessible = true }.invoke(service)
            }
            Thread.sleep(300)
            val notifications = context.getSystemService(android.app.NotificationManager::class.java)
            assertFalse(notifications.activeNotifications.any { it.id == MediaPlaybackService.NOTIFICATION_ID })
            instrumentation.runOnMainSync { service.togglePlayPause() }
            Thread.sleep(1000)
            instrumentation.runOnMainSync {
                assertTrue(service.isPlaying()); assertTrue(session.isActive)
                assertFalse(service.mediaNotificationDismissed)
                assertFalse(dismissalPrefs.getBoolean(MediaPlaybackService.PREF_MEDIA_DISMISSED, true))
                assertTrue(service.getCurrentPosition() >= savedPosition)
                assertEquals(track, service.getCurrentTrack()); assertEquals(queue, playlist.get(service))
                assertNotNull(player.get(service))
            }
            assertTrue(notifications.activeNotifications.any { it.id == MediaPlaybackService.NOTIFICATION_ID })
        } finally {
            instrumentation.runOnMainSync { service.clearPlayback(); service.onNotificationClose = null; service.onNotificationFavorite = null; activity.finish() }
            context.unbindService(connection); file.delete()
        }
    }
}
