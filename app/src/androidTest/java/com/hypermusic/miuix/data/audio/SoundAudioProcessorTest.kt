package com.hypermusic.miuix.data.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class SoundAudioProcessorTest {
    @Test fun neutralAndChangedSettingsApplyToPcmInRealTime() {
        AudioSettings.init(ApplicationProvider.getApplicationContext())
        val previous = AudioSettings.current
        val mode = AudioSettings.loudness
        try {
            AudioSettings.set(SoundEffects())
            AudioSettings.updateLoudness(LoudnessMode.OFF)
            val processor = SoundAudioProcessor()
            processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
            processor.flush()
            fun input() = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).putShort(16000).putShort((-8000).toShort()).apply { flip() }
            processor.queueInput(input())
            val original = processor.output.order(ByteOrder.nativeOrder())
            assertEquals(16000, original.short.toInt())
            assertEquals(-8000, original.short.toInt())
            AudioSettings.set(SoundEffects(width = 0f))
            processor.queueInput(input())
            val mixed = processor.output.order(ByteOrder.nativeOrder())
            val left = mixed.short.toInt(); val right = mixed.short.toInt()
            assertEquals(left, right)
            assertTrue(kotlin.math.abs(4000 - left) <= 1)
            processor.reset()
        } finally { AudioSettings.set(previous); AudioSettings.updateLoudness(mode) }
    }
    @Test fun exoPlayerSendsDecodedPcmThroughProcessor() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        AudioSettings.init(context)
        val file = java.io.File(context.cacheDir, "sound-processor-test.wav")
        val frames = 48000
        val pcm = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        fun ascii(text: String) { pcm.put(text.toByteArray(Charsets.US_ASCII)) }
        ascii("RIFF"); pcm.putInt(36 + frames * 4); ascii("WAVE"); ascii("fmt "); pcm.putInt(16)
        pcm.putShort(1); pcm.putShort(2); pcm.putInt(48000); pcm.putInt(48000 * 4); pcm.putShort(4); pcm.putShort(16)
        ascii("data"); pcm.putInt(frames * 4)
        repeat(frames) { pcm.putShort(500); pcm.putShort((-500).toShort()) }
        file.writeBytes(pcm.array())
        val processor = SoundAudioProcessor()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        var player: androidx.media3.exoplayer.ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
                    override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink =
                        androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context).setEnableFloatOutput(false)
                            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams).setAudioProcessors(arrayOf(processor)).build()
                }
                player = androidx.media3.exoplayer.ExoPlayer.Builder(context, renderers).build().apply {
                    volume = 0f
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
                    prepare(); play()
                }
            }
            val deadline = System.currentTimeMillis() + 10000
            while (processor.processedFrames == 0L && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertTrue("ExoPlayer did not send decoded audio into the effects processor", processor.processedFrames > 0)
        } finally {
            instrumentation.runOnMainSync { player?.release() }
            file.delete()
        }
    }

    @Test fun exoPlayerExposesReplayGainTagsForTrackAndAlbum() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File(context.cacheDir, "replaygain-test.flac")
        instrumentation.context.assets.open("replaygain-test.flac").use { input -> file.outputStream().use { input.copyTo(it) } }
        val found = java.util.concurrent.atomic.AtomicReference(ReplayGainInfo())
        var player: androidx.media3.exoplayer.ExoPlayer? = null
        fun collect(metadata: androidx.media3.common.Metadata?) {
            if (metadata == null) return
            var current = found.get()
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                if (entry is androidx.media3.extractor.metadata.flac.VorbisComment) current = current.withTag(entry.key, entry.value)
            }
            found.set(current)
        }
        try {
            instrumentation.runOnMainSync {
                player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onMetadata(metadata: androidx.media3.common.Metadata) = collect(metadata)
                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            for (group in tracks.groups) for (index in 0 until group.length) {
                                if (group.isTrackSelected(index)) collect(group.getTrackFormat(index).metadata)
                            }
                        }
                    })
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(file)))
                    prepare(); play()
                }
            }
            val deadline = System.currentTimeMillis() + 10000
            while (found.get().albumDb == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertEquals(-6f, found.get().trackDb ?: Float.NaN, .001f)
            assertEquals(-3f, found.get().albumDb ?: Float.NaN, .001f)
        } finally { instrumentation.runOnMainSync { player?.release() }; file.delete() }
    }

    @Test fun presetsCanBeSavedOverwrittenAndSelected() {
        AudioSettings.init(ApplicationProvider.getApplicationContext())
        val original = AudioSettings.current
        val originalSelection = AudioSettings.selectedId
        val baseline = AudioSettings.builtInEffects("flat")!!
        var id: String? = null
        try {
            AudioSettings.select("flat")
            val changed = baseline.copy(width = .53f)
            AudioSettings.set(changed)
            assertTrue(AudioSettings.modified)
            assertTrue(AudioSettings.overwriteSelected())
            assertFalse(AudioSettings.modified)
            AudioSettings.select("bass")
            AudioSettings.select("flat")
            assertEquals(changed, AudioSettings.current)
            val saved = AudioSettings.saveNew("test-effect", changed)
            id = saved.id
            AudioSettings.set(changed.copy(width = .54f))
            assertTrue(AudioSettings.overwriteSelected())
            AudioSettings.select("flat")
            AudioSettings.select(saved.id)
            assertEquals(.54f, AudioSettings.current.width, .001f)
        } finally {
            id?.let { AudioSettings.remove(it) }
            AudioSettings.select("flat"); AudioSettings.set(baseline); AudioSettings.overwriteSelected()
            AudioSettings.select(originalSelection); AudioSettings.set(original)
        }
    }

    @Test fun playbackServiceWiresAudioProcessorIntoItsPlayer() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val instrument = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        AudioSettings.init(context)
        val previous = AudioSettings.current
        val previousMode = AudioSettings.loudness
        val frames = 48000 * 2
        val wav = ByteBuffer.allocate(44 + frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        fun ascii(s: String) { wav.put(s.toByteArray(Charsets.US_ASCII)) }
        ascii("RIFF"); wav.putInt(36 + frames * 4); ascii("WAVE"); ascii("fmt "); wav.putInt(16)
        wav.putShort(1); wav.putShort(2); wav.putInt(48000); wav.putInt(48000 * 4); wav.putShort(4); wav.putShort(16)
        ascii("data"); wav.putInt(frames * 4)
        repeat(frames) { wav.putShort(0); wav.putShort(0) }
        val server = java.net.ServerSocket(0)
        val thread = Thread {
            while (!server.isClosed) try {
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader()
                    var line: String?
                    do { line = request.readLine() } while (line != null && line!!.isNotEmpty())
                    val output = socket.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${wav.array().size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(wav.array()); output.flush()
                }
            } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
        val connected = java.util.concurrent.CountDownLatch(1)
        var service: com.hypermusic.miuix.service.MediaPlaybackService? = null
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                service = (binder as com.hypermusic.miuix.service.MediaPlaybackService.LocalBinder).getService(); connected.countDown()
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) { service = null }
        }
        try {
            AudioSettings.set(SoundEffects(width = 0f)); AudioSettings.updateLoudness(LoudnessMode.OFF)
            assertTrue(context.bindService(android.content.Intent(context, com.hypermusic.miuix.service.MediaPlaybackService::class.java), connection, android.content.Context.BIND_AUTO_CREATE))
            assertTrue(connected.await(10, java.util.concurrent.TimeUnit.SECONDS))
            val track = com.hypermusic.miuix.data.model.Track(id = "test-audio", title = "test", artist = "test", artistId = "", album = "", albumId = "", sourceUrl = "", imgUrl = "")
            instrument.runOnMainSync { service!!.playTrackWithUrl(track, "http://127.0.0.1:${server.localPort}/test.wav") }
            val field = com.hypermusic.miuix.service.MediaPlaybackService::class.java.getDeclaredField("audioProcessor").apply { isAccessible = true }
            val deadline = System.currentTimeMillis() + 12000
            var processed = false
            while (!processed && System.currentTimeMillis() < deadline) {
                processed = (field.get(service) as? SoundAudioProcessor)?.processedFrames?.let { it > 0 } == true
                if (!processed) Thread.sleep(100)
            }
            assertTrue("Playback service did not process decoded PCM", processed)
        } finally {
            instrument.runOnMainSync { service?.clearPlayback() }
            runCatching { context.unbindService(connection) }
            server.close(); thread.join(1000)
            AudioSettings.set(previous); AudioSettings.updateLoudness(previousMode)
        }
    }

}
