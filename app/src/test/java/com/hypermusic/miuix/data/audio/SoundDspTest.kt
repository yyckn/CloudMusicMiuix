package com.hypermusic.miuix.data.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SoundDspTest {
    @Test fun neutralIsBitExact() {
        val dsp = SoundDsp(48000, 2)
        val frame = floatArrayOf(.125f, -.75f)
        dsp.process(frame)
        assertArrayEquals(floatArrayOf(.125f, -.75f), frame, 0f)
    }
    @Test fun widthZeroProducesMono() {
        val dsp = SoundDsp(48000, 2)
        dsp.configure(SoundEffects(width = 0f), 0f)
        val frame = floatArrayOf(.8f, -.2f)
        dsp.process(frame)
        assertEquals(frame[0], frame[1], .00001f)
    }
    @Test fun gainChangesAmplitudeAndClipsSafely() {
        val dsp = SoundDsp(48000, 1)
        dsp.configure(SoundEffects(), -6f)
        val frame = floatArrayOf(.8f)
        repeat(24000) { frame[0] = .8f; dsp.process(frame) }
        assertEquals(.8f * .501187f, frame[0], .0001f)
    }
    @Test fun highpassAttenuatesLowFrequency() {
        val dsp = SoundDsp(48000, 1)
        dsp.configure(SoundEffects(highpass = 500f), 0f)
        var energy = 0.0
        repeat(48000) { index ->
            val frame = floatArrayOf((.3 * sin(2 * PI * 40 * index / 48000)).toFloat())
            dsp.process(frame)
            if (index > 10000) energy += frame[0] * frame[0]
        }
        assertTrue(energy / 38000 < .0001)
    }
    @Test fun presetsProduceFiniteOutput() {
        for (preset in SoundPresets.all) {
            val dsp = SoundDsp(44100, 2)
            dsp.configure(preset.effects, 0f)
            repeat(20000) { i ->
                val frame = floatArrayOf(sin(i * .02).toFloat(), sin(i * .03).toFloat())
                dsp.process(frame)
                assertTrue(frame.all { it.isFinite() && it in -1f..1f })
            }
        }
    }
}
