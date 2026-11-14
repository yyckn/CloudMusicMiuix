package com.hypermusic.miuix.data.audio

import kotlin.math.log10
import kotlin.math.pow

/** ReplayGain tags are optional; absent or malformed tags never change playback gain. */
data class ReplayGainInfo(
    val trackDb: Float? = null,
    val trackPeak: Float? = null,
    val albumDb: Float? = null,
    val albumPeak: Float? = null,
) {
    fun withTag(key: String, value: String): ReplayGainInfo {
        val number = value.trim().removeSuffix("dB").removeSuffix("DB").trim().toFloatOrNull()?.takeIf(Float::isFinite) ?: return this
        return when (key.uppercase().replace('-', '_')) {
            "REPLAYGAIN_TRACK_GAIN" -> copy(trackDb = number.coerceIn(-30f, 12f))
            "REPLAYGAIN_TRACK_PEAK" -> copy(trackPeak = number.takeIf { it > 0f })
            "REPLAYGAIN_ALBUM_GAIN" -> copy(albumDb = number.coerceIn(-30f, 12f))
            "REPLAYGAIN_ALBUM_PEAK" -> copy(albumPeak = number.takeIf { it > 0f })
            else -> this
        }
    }
    fun gainFor(mode: LoudnessMode, albumPlayback: Boolean): Float = when (mode) {
        LoudnessMode.TRACK -> limitedGain(trackDb, trackPeak)
        LoudnessMode.ALBUM -> if (albumPlayback) limitedGain(albumDb, albumPeak) else 0f
        else -> 0f
    }
    private fun limitedGain(db: Float?, peak: Float?): Float {
        val gain = db ?: return 0f
        if (peak == null || peak <= 0f) return gain.coerceAtMost(0f)
        return minOf(gain, (-20.0 * log10(peak.toDouble())).toFloat()).coerceIn(-30f, 12f)
    }
}

/** Conservative, gated power estimate; adjusts once after at least ten seconds of audio. */
class SmartLoudness(private val sampleRate: Int, private val channels: Int) {
    private var squareSum = 0.0
    private var includedSamples = 0L
    private var frames = 0L
    private var peak = 0.0
    var gainDb: Float = 0f
        private set
    private var settled = false
    fun observe(frame: FloatArray) {
        if (settled) return
        var energy = 0.0
        for (ch in 0 until channels) {
            val value = frame[ch].toDouble()
            energy += value * value
            peak = maxOf(peak, kotlin.math.abs(value))
        }
        energy /= channels
        if (energy > 0.0000001) { squareSum += energy; includedSamples++ }
        frames++
        if (frames >= sampleRate * 10L && includedSamples >= sampleRate * 3L) {
            val rmsDb = 10.0 * log10(squareSum / includedSamples)
            val desired = -18.0 - rmsDb
            val peakLimit = if (peak > 0) -20.0 * log10(peak) else 0.0
            gainDb = minOf(desired, peakLimit).coerceIn(-18.0, 6.0).toFloat()
            settled = true
        }
    }
}
