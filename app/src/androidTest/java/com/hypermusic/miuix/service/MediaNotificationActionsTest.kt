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
    @Test fun fallbackCloseClearsOnlyCurrentPlatformSnapshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
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
                assertFalse(prefs.contains("playback_snapshot_$source"))
                snapshotKeys.filter { it != "playback_snapshot_$source" }.forEach { assertEquals("saved", prefs.getString(it, null)) }
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
                trackField.set(service, track)
                playerField.set(service, createPlayer.invoke(service, track))
                service.onNotificationFavorite = { if (it == track) favorite.countDown() }
                service.onNotificationClose = { service.clearPlayback(); closed.countDown() }
                update.invoke(service, PlaybackStateCompat.STATE_PAUSED)
                session = sessionField.get(service) as MediaSessionCompat
            }
            val actions = session.controller.playbackState.customActions.map { it.action }
            assertTrue(actions.contains(MediaPlaybackService.ACTION_CLOSE))
            assertTrue(actions.contains(MediaPlaybackService.ACTION_FAVORITE))
            session.controller.transportControls.sendCustomAction(MediaPlaybackService.ACTION_FAVORITE, null)
            assertTrue(favorite.await(10, TimeUnit.SECONDS))
            session.controller.transportControls.sendCustomAction(MediaPlaybackService.ACTION_CLOSE, null)
            assertTrue(closed.await(10, TimeUnit.SECONDS))
            assertNull(trackField.get(service))
            assertNull(playerField.get(service))
            assertFalse(session.isActive)
        } finally {
            context.unbindService(connection)
        }
    }
}
