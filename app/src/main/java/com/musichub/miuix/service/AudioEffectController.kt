package com.musichub.miuix.service

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.media3.common.C
import com.musichub.miuix.data.model.AudioEffectPreset
import com.musichub.miuix.data.model.AudioEffectPresets
import com.musichub.miuix.data.model.AudioReverb

/**
 * 将内置音效预设应用到 ExoPlayer 的音频会话。
 * 设备不支持某个效果时自动跳过，不影响正常播放。
 */
@Suppress("DEPRECATION")
class AudioEffectController {

    private var sessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var preset: AudioEffectPreset = AudioEffectPresets.default

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null
    private var virtualizer: Virtualizer? = null

    fun setPreset(newPreset: AudioEffectPreset) {
        preset = newPreset
        if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
            applyPreset()
        }
    }

    fun setPresetById(id: String) {
        setPreset(AudioEffectPresets.byId(id))
    }

    fun onAudioSessionChanged(newSessionId: Int) {
        if (newSessionId == C.AUDIO_SESSION_ID_UNSET || newSessionId == sessionId) return
        releaseEffects()
        sessionId = newSessionId
        applyPreset()
    }

    private fun applyPreset() {
        if (preset.id == AudioEffectPresets.DEFAULT_ID) {
            releaseEffects()
            return
        }
        try {
            ensureEffects()
            applyEqualizer()
            applyBassBoost()
            applyLoudness()
            applyReverb()
            applyVirtualizer()
        } catch (e: Exception) {
            Log.e(TAG, "applyPreset failed: ${e.message}")
        }
    }

    private fun ensureEffects() {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

        if (equalizer == null) {
            runCatching { Equalizer(0, sessionId) }
                .onSuccess { equalizer = it }
                .onFailure { Log.w(TAG, "Equalizer unavailable: ${it.message}") }
        }
        if (bassBoost == null) {
            runCatching { BassBoost(0, sessionId) }
                .onSuccess { bassBoost = it }
                .onFailure { Log.w(TAG, "BassBoost unavailable: ${it.message}") }
        }
        if (loudnessEnhancer == null) {
            runCatching { LoudnessEnhancer(sessionId) }
                .onSuccess { loudnessEnhancer = it }
                .onFailure { Log.w(TAG, "LoudnessEnhancer unavailable: ${it.message}") }
        }
        if (presetReverb == null) {
            runCatching { PresetReverb(0, sessionId) }
                .onSuccess { presetReverb = it }
                .onFailure { Log.w(TAG, "PresetReverb unavailable: ${it.message}") }
        }
        if (virtualizer == null) {
            runCatching { Virtualizer(0, sessionId) }
                .onSuccess { virtualizer = it }
                .onFailure { Log.w(TAG, "Virtualizer unavailable: ${it.message}") }
        }
    }

    private fun applyEqualizer() {
        val eq = equalizer ?: return
        val bandCount = eq.numberOfBands.toInt()
        if (bandCount <= 0) return
        val range = eq.bandLevelRange
        val minLevel = range[0].toInt()
        val maxLevel = range[1].toInt()

        for (band in 0 until bandCount) {
            val level = sampleBandLevel(preset.bandLevels, band, bandCount)
                .coerceIn(minLevel, maxLevel)
            eq.setBandLevel(band.toShort(), level.toShort())
        }
        runCatching { eq.enabled = true }
    }

    /** 将 5 段控制曲线线性映射到设备实际频段数。 */
    private fun sampleBandLevel(levels: List<Short>, band: Int, bandCount: Int): Int {
        if (levels.isEmpty()) return 0
        if (bandCount <= 1) return levels[levels.size / 2].toInt()
        val position = band.toFloat() / (bandCount - 1) * (levels.size - 1)
        val low = position.toInt().coerceIn(0, levels.lastIndex)
        val high = (low + 1).coerceAtMost(levels.lastIndex)
        val fraction = position - low
        return (levels[low] + (levels[high] - levels[low]) * fraction).toInt()
    }

    private fun applyBassBoost() {
        val boost = bassBoost ?: return
        val strength = preset.bassBoost.toInt().coerceIn(0, 1000)
        runCatching {
            boost.setStrength(strength.toShort())
            boost.enabled = strength > 0
        }
    }

    private fun applyLoudness() {
        val enhancer = loudnessEnhancer ?: return
        val gain = preset.loudnessGain.coerceAtLeast(0)
        runCatching {
            enhancer.setTargetGain(gain)
            enhancer.enabled = gain > 0
        }
    }

    private fun applyReverb() {
        val reverb = presetReverb ?: return
        runCatching {
            if (preset.reverb == AudioReverb.LARGE_HALL) {
                reverb.preset = PresetReverb.PRESET_LARGEHALL
                reverb.enabled = true
            } else {
                reverb.enabled = false
            }
        }
    }

    private fun applyVirtualizer() {
        val spatial = virtualizer ?: return
        val strength = preset.virtualizerStrength.toInt().coerceIn(0, 1000)
        runCatching {
            spatial.setStrength(strength.toShort())
            if (strength > 0) {
                // 强制双耳虚拟化，让声场扩展更明显（设备不支持时会被忽略）
                spatial.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
            }
            spatial.enabled = strength > 0
        }
    }

    private fun releaseEffects() {
        runCatching {
            equalizer?.let {
                it.enabled = false
                it.release()
            }
        }
        runCatching {
            bassBoost?.let {
                it.enabled = false
                it.release()
            }
        }
        runCatching {
            loudnessEnhancer?.let {
                it.enabled = false
                it.release()
            }
        }
        runCatching {
            presetReverb?.let {
                it.enabled = false
                it.release()
            }
        }
        runCatching {
            virtualizer?.let {
                it.enabled = false
                it.release()
            }
        }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
        presetReverb = null
        virtualizer = null
    }

    fun release() {
        releaseEffects()
        sessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private companion object {
        const val TAG = "AudioEffectController"
    }
}
