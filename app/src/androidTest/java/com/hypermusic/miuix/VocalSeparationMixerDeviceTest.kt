package com.hypermusic.miuix

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.data.audio.AudioSettings
import com.hypermusic.miuix.data.audio.LoudnessMode
import com.hypermusic.miuix.data.audio.SoundAudioProcessor
import com.hypermusic.miuix.data.audio.SoundEffects
import com.hypermusic.miuix.data.audio.separation.VocalSeparationCache
import com.hypermusic.miuix.data.audio.separation.VocalSeparationEngine
import com.hypermusic.miuix.data.audio.separation.VocalSeparationMix
import com.hypermusic.miuix.data.audio.separation.VocalSeparationStatus
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class VocalSeparationMixerDeviceTest {
    @Test
    fun mixesReadOnlyPcmAtItsMediaTimeAndAdjustsVocalVolume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val engine = VocalSeparationEngine
        engine.init(context)
        AudioSettings.init(context)
        val previousEffects = AudioSettings.current
        val previousLoudness = AudioSettings.loudness
        val previousSeparation = engine.uiState.value
        val trackKey = "test:${UUID.randomUUID()}"
        val sampleRate = VocalSeparationCache.SAMPLE_RATE
        val cache = VocalSeparationCache(context)
        val entry = cache.commitEntry(
            songKey = trackKey,
            title = "Mixer test",
            artist = "",
            album = "",
            modelId = previousSeparation.model.id,
            totalFrames = sampleRate * 2,
            durationMs = 2000L,
            vocalsTemp = writeStem(context.cacheDir, sampleRate, 0, 8000),
            instTemp = writeStem(context.cacheDir, sampleRate, 2000, 4000),
        )
        val processor = SoundAudioProcessor()
        try {
            instrumentation.runOnMainSync {
                AudioSettings.set(SoundEffects())
                AudioSettings.updateLoudness(LoudnessMode.OFF)
                engine.setEnabled(true)
                engine.setMix(VocalSeparationMix(0))
                engine.notifyTrackChanged(trackKey, "Mixer test", "", "", "file:///unused")
            }
            assertTrue(engine.uiState.value.status is VocalSeparationStatus.Ready)
            processor.configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
            processor.flush()
            assertEquals(4000, processReadOnlyPcm(processor, 1_000_000L))

            val expectedByPosition = mapOf(
                0 to 4000, 1 to 4800, 5 to 8000, 9 to 11200, 10 to 12000,
                11 to 11600, 15 to 10000, 19 to 8400, 20 to 8000,
            )
            expectedByPosition.forEach { (position, expected) ->
                instrumentation.runOnMainSync { engine.setMix(VocalSeparationMix(position)) }
                assertEquals("Slider position $position", expected, processReadOnlyPcm(processor, 1_050_000L))
            }
            instrumentation.runOnMainSync { engine.setMix(VocalSeparationMix.ORIGINAL) }
            processor.flush()
            assertEquals(2000, processReadOnlyPcm(processor, 0L))
            assertEquals(1000, processReadOnlyPcm(processor, 2_000_000L))
        } finally {
            processor.reset()
            instrumentation.runOnMainSync {
                engine.clearTrack()
                engine.setEnabled(previousSeparation.enabled)
                engine.setMix(previousSeparation.mix)
                AudioSettings.set(previousEffects)
                AudioSettings.updateLoudness(previousLoudness)
            }
            cache.deleteEntry(entry.id)
        }
    }

    private fun processReadOnlyPcm(processor: SoundAudioProcessor, timeUs: Long): Int {
        val pcm = ByteBuffer.allocateDirect(2048 * 4).order(ByteOrder.nativeOrder())
        while (pcm.hasRemaining()) pcm.putShort(1000)
        pcm.flip()
        val input = pcm.asReadOnlyBuffer().order(ByteOrder.nativeOrder())
        processor.mediaTimeUs = timeUs
        processor.queueInput(input)
        assertEquals(input.limit(), input.position())
        val output = processor.output
        assertEquals(pcm.remaining(), output.remaining())
        return output.order(ByteOrder.nativeOrder()).short.toInt()
    }

    private fun writeStem(directory: File, sampleRate: Int, first: Short, second: Short): File {
        val pcm = ByteBuffer.allocate(sampleRate * 8).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleRate * 2) { pcm.putShort(first) }
        repeat(sampleRate * 2) { pcm.putShort(second) }
        return File.createTempFile("mixer-test-", ".pcm", directory).apply { writeBytes(pcm.array()) }
    }
}
