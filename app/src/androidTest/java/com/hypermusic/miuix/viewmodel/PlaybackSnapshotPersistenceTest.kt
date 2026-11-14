package com.hypermusic.miuix.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.PlatformManager
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackSnapshotPersistenceTest {
    @Test fun pendingTrackDoesNotInheritPreviousPlayerPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Requires a disposable test application ID", context.packageName.endsWith(".platformtest"))
        val connected = java.util.concurrent.CountDownLatch(1)
        lateinit var service: com.hypermusic.miuix.service.MediaPlaybackService
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                service = (binder as com.hypermusic.miuix.service.MediaPlaybackService.LocalBinder).getService()
                connected.countDown()
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) = Unit
        }
        assertTrue(context.bindService(android.content.Intent(context, com.hypermusic.miuix.service.MediaPlaybackService::class.java), connection, android.content.Context.BIND_AUTO_CREATE))
        val store = ViewModelStore()
        try {
            assertTrue(connected.await(10, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                PlatformManager.init(context)
                PlatformManager.select(MusicPlatform.BODIAN)
                NeteaseApi.init(context)
                val old = Track(id = "bodian_1", title = "Old", artist = "Artist", artistId = "", album = "", albumId = "", source = "bodian", sourceUrl = "", imgUrl = "")
                val next = old.copy(id = "bodian_2", title = "New")
                val serviceType = com.hypermusic.miuix.service.MediaPlaybackService::class.java
                serviceType.getDeclaredField("currentTrack").apply { isAccessible = true }.set(service, old)
                serviceType.getDeclaredField("playbackPosition").apply { isAccessible = true }.setLong(service, 32_000)
                val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
                val model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[MusicPlayerViewModel::class.java]
                MusicPlayerViewModel::class.java.getDeclaredField("mediaPlaybackService").apply { isAccessible = true }.set(model, service)
                model.playTrackWithMode(next, listOf(old, next))
                store.clear()
                val snapshot = Gson().fromJson(context.getSharedPreferences("player_prefs", 0).getString("playback_snapshot_bodian", null), PlatformPlaybackSnapshot::class.java)
                assertEquals(next.id, snapshot.track?.id)
                assertEquals(0L, snapshot.position)
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            context.unbindService(connection)
        }
    }

    @Test fun newSourceSnapshotsRestorePausedWithTheirOwnQueue() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Requires a disposable test application ID", context.packageName.endsWith(".platformtest"))
        val prefs = context.getSharedPreferences("player_prefs", 0)
        for (platform in listOf(MusicPlatform.BODIAN, MusicPlatform.KUGOU, MusicPlatform.QISHUI)) {
            val store = ViewModelStore()
            lateinit var model: MusicPlayerViewModel
            val one = Track(id = "test_1", title = "First", artist = "Artist", artistId = "", album = "", albumId = "", source = platform.trackSource, sourceUrl = "", imgUrl = "")
            val two = one.copy(id = "test_2", title = "Second")
            try {
                instrumentation.runOnMainSync {
                    PlatformManager.init(context)
                    PlatformManager.select(platform)
                    NeteaseApi.init(context)
                    val snapshot = PlatformPlaybackSnapshot(track = two, playlist = listOf(one, two), position = 4200)
                    prefs.edit().putString("playback_snapshot_${platform.storageKey}", Gson().toJson(snapshot)).commit()
                    val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
                    model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[MusicPlayerViewModel::class.java]
                }
                val deadline = android.os.SystemClock.uptimeMillis() + 10_000
                var restored = false
                while (!restored && android.os.SystemClock.uptimeMillis() < deadline) {
                    instrumentation.runOnMainSync { restored = model.playerState.currentTrack?.id == two.id }
                    if (!restored) android.os.SystemClock.sleep(50)
                }
                assertTrue("$platform snapshot was not restored", restored)
                instrumentation.runOnMainSync {
                    assertEquals(platform.trackSource, model.playerState.currentTrack?.source)
                    assertEquals(listOf(one.id, two.id), model.currentPlaylist.map { it.id })
                    assertEquals(4200L, model.playerState.currentPosition)
                    org.junit.Assert.assertFalse(model.playerState.isPlaying)
                }
            } finally {
                instrumentation.runOnMainSync { store.clear() }
            }
        }
    }

    @Test fun newSourceTrackAndQueueAreSavedBeforeAudioResolution() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Requires a disposable test application ID", context.packageName.endsWith(".platformtest"))
        val prefs = context.getSharedPreferences("player_prefs", 0)
        for (platform in listOf(MusicPlatform.BODIAN, MusicPlatform.KUGOU, MusicPlatform.QISHUI)) {
            val store = ViewModelStore()
            try {
                instrumentation.runOnMainSync {
                    PlatformManager.init(context)
                    PlatformManager.select(platform)
                    NeteaseApi.init(context)
                    prefs.edit().remove("playback_snapshot_${platform.storageKey}").putInt("play_mode", 0).commit()
                    val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
                    val model = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application))[MusicPlayerViewModel::class.java]
                    val one = Track(id = "test_1", title = "First", artist = "Artist", artistId = "", album = "", albumId = "", source = platform.trackSource, sourceUrl = "", imgUrl = "")
                    val two = one.copy(id = "test_2", title = "Second")
                    model.playTrackWithMode(two, listOf(one, two))
                    val snapshot = Gson().fromJson(prefs.getString("playback_snapshot_${platform.storageKey}", null), PlatformPlaybackSnapshot::class.java)
                    assertEquals(two.id, snapshot.track?.id)
                    assertEquals(listOf(one.id, two.id), snapshot.playlist.map { it.id })
                    assertEquals(0L, snapshot.position)
                    assertTrue(snapshot.belongsTo(platform))
                    store.clear()
                }
            } finally {
                instrumentation.runOnMainSync { store.clear() }
            }
        }
    }
}
