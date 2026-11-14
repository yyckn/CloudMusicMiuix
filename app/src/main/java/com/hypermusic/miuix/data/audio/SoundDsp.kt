package com.hypermusic.miuix.data.audio

import kotlin.math.*

/** Stateful per-stream PCM processing. A new instance is used for each playback stream. */
class SoundDsp(private val sampleRate: Int, private val channels: Int) {
    private class Biquad(private val channels: Int) {
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
        private var a1 = 0.0; private var a2 = 0.0
        private val z1 = DoubleArray(channels); private val z2 = DoubleArray(channels)
        fun set(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
            this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0
            this.a1 = a1 / a0; this.a2 = a2 / a0
        }
        fun clear() { z1.fill(0.0); z2.fill(0.0) }
        fun process(x: Double, channel: Int): Double {
            val y = b0 * x + z1[channel]
            z1[channel] = b1 * x - a1 * y + z2[channel]
            z2[channel] = b2 * x - a2 * y
            return y
        }
    }
    private val filters = Array(12) { Biquad(channels) }
    private val delayFrames = max(8, (sampleRate * .05).toInt())
    private val delay = Array(channels) { DoubleArray(delayFrames) }
    private val reverbFrames = max(8, (sampleRate * .091).toInt())
    private val reverb = Array(channels) { DoubleArray(reverbFrames + it * max(1, sampleRate / 1000)) }
    private val reverbPos = IntArray(channels)
    private var delayPos = 0
    private var phase = 0.0
    private var noiseSeed = 0x12345678
    private var envelope = 0.0
    private var config = SoundEffects()
    private var gain = 1.0
    private var targetGain = 1.0
    private var configuredGainDb = 0f
    private var effectsNeutral = true
    private val gainSmoothing = 1.0 - exp(-1.0 / (sampleRate * .05))
    private var bypass = true
    init { require(sampleRate > 0 && channels in 1..8); configure(config, 0f) }

    fun configure(effects: SoundEffects, gainDb: Float) {
        require(effects.valid() && gainDb.isFinite())
        if (effects == config && gainDb == configuredGainDb) return
        config = effects
        effectsNeutral = effects == SoundEffects()
        configuredGainDb = gainDb
        targetGain = 10.0.pow(gainDb.coerceIn(-30f, 12f).toDouble() / 20.0)
        bypass = effectsNeutral && gainDb == 0f && abs(gain - 1.0) < 0.00001
        for ((index, frequency) in SoundPresets.frequencies.withIndex()) {
            val f = frequency.toDouble().coerceAtMost(sampleRate * .45)
            val a = 10.0.pow(effects.bands[index] / 40.0)
            val w = 2 * PI * f / sampleRate; val alpha = sin(w) / (2 * 1.0)
            val c = cos(w)
            if (index == 0 || index == SoundPresets.frequencies.lastIndex) {
                // Folia's outer bands are shelving filters; the inner bands are peaking filters.
                val beta = 2 * sqrt(a) * sin(w) / sqrt(2.0)
                if (index == 0) {
                    filters[index].set(
                        a * ((a + 1) - (a - 1) * c + beta),
                        2 * a * ((a - 1) - (a + 1) * c),
                        a * ((a + 1) - (a - 1) * c - beta),
                        (a + 1) + (a - 1) * c + beta,
                        -2 * ((a - 1) + (a + 1) * c),
                        (a + 1) + (a - 1) * c - beta)
                } else {
                    filters[index].set(
                        a * ((a + 1) + (a - 1) * c + beta),
                        -2 * a * ((a - 1) + (a + 1) * c),
                        a * ((a + 1) + (a - 1) * c - beta),
                        (a + 1) - (a - 1) * c + beta,
                        2 * ((a - 1) - (a + 1) * c),
                        (a + 1) - (a - 1) * c - beta)
                }
            } else {
                filters[index].set(1 + alpha * a, -2*c, 1-alpha*a, 1+alpha/a, -2*c, 1-alpha/a)
            }
        }
        fun cutoff(filter: Biquad, hz: Float, highpass: Boolean) {
            val w = 2 * PI * hz.coerceAtMost(sampleRate * .45f) / sampleRate
            val c = cos(w); val a = sin(w) / (2 * .707)
            val b0 = if (highpass) (1+c)/2 else (1-c)/2
            val b1 = if (highpass) -(1+c) else 1-c
            filter.set(b0,b1,b0,1+a,-2*c,1-a)
        }
        cutoff(filters[10], effects.highpass, true); cutoff(filters[11], effects.lowpass, false)
    }

    fun reset() {
        filters.forEach { it.clear() }; delay.forEach { it.fill(0.0) }; reverb.forEach { it.fill(0.0) }
        reverbPos.fill(0); delayPos = 0; phase = 0.0; envelope = 0.0
    }

    /** Modifies one interleaved frame. Neutral settings preserve samples exactly. */
    fun process(frame: FloatArray) {
        require(frame.size >= channels)
        if (bypass) return
        val e = config
        for (ch in 0 until channels) {
            var x = frame[ch].toDouble()
            for (i in 0..9) if (e.bands[i] != 0f) x = filters[i].process(x, ch)
            if (e.highpass > 20f) x = filters[10].process(x, ch)
            if (e.lowpass < 20000f) x = filters[11].process(x, ch)
            if (e.drive > 0f) {
                val amount = 1.0 + e.drive * 8.0
                x = tanh(x * amount) / tanh(amount)
            }
            if (e.crush > 0f) {
                val levels = 2.0.pow(16.0 - 12.0 * e.crush)
                x = round(x * levels) / levels
            }
            delay[ch][delayPos] = x
            if (e.wow > 0f) {
                val wobble = 0.012 + e.wow * (.0045 * sin(phase) + .001 * sin(phase * 7.0))
                val samples = (wobble * sampleRate).coerceIn(1.0, (delayFrames - 2).toDouble())
                val whole = samples.toInt(); val fraction = samples - whole
                val a = delay[ch][(delayPos - whole + delayFrames) % delayFrames]
                val b = delay[ch][(delayPos - whole - 1 + delayFrames) % delayFrames]
                x = a * (1-fraction) + b * fraction
            }
            if (e.noise > 0f) {
                noiseSeed = noiseSeed * 1664525 + 1013904223
                x += (noiseSeed.toDouble() / Int.MAX_VALUE) * e.noise * .02
            }
            if (e.space > 0f) {
                val pos = reverbPos[ch]; val tail = reverb[ch][pos]
                reverb[ch][pos] = x + tail * .55
                reverbPos[ch] = (pos + 1) % reverb[ch].size
                x = x * (1 - e.space * .25) + tail * e.space * .35
            }
            frame[ch] = x.toFloat()
        }
        delayPos = (delayPos + 1) % delayFrames
        phase += 2 * PI * .7 / sampleRate
        if (phase > 2 * PI) phase -= 2 * PI
        if (channels >= 2 && e.width != 1f) {
            val mid = (frame[0] + frame[1]) * .5f
            val side = (frame[0] - frame[1]) * .5f * e.width
            frame[0] = mid + side; frame[1] = mid - side
        }
        if (e.punch > 0f) {
            var level = 0.0
            for (ch in 0 until channels) level = max(level, abs(frame[ch]).toDouble())
            val coefficient = if (level > envelope) .002 else .00005
            envelope += (level - envelope) * coefficient
            val threshold = 1.0 - .75 * e.punch
            if (envelope > threshold) {
                val reduction = (threshold / envelope).pow(e.punch.toDouble() * .75)
                for (ch in 0 until channels) frame[ch] = (frame[ch] * reduction).toFloat()
            }
        }
        // Smooth changes over roughly 250 ms to avoid clicks when metadata or settings change.
        gain += (targetGain - gain) * gainSmoothing
        for (ch in 0 until channels) frame[ch] = (frame[ch] * gain).coerceIn(-1.0, 1.0).toFloat()
        if (effectsNeutral && targetGain == 1.0 && abs(gain - 1.0) < 0.00001) {
            gain = 1.0
            bypass = true
        }
    }
}
