package com.hypermusic.miuix.data.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** PCM processor inserted into ExoPlayer's AudioSink; reads immutable settings once per buffer. */
@OptIn(UnstableApi::class)
class SoundAudioProcessor : BaseAudioProcessor() {
    @Volatile var replayGain: ReplayGainInfo = ReplayGainInfo()
    @Volatile var albumPlayback: Boolean = false
    private var dsp: SoundDsp? = null
    private var meter: SmartLoudness? = null
    private var frame = FloatArray(0)
    private var lastEffects: SoundEffects? = null
    private var lastGain = Float.NaN
    @Volatile var processedFrames: Long = 0
        private set

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..8) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }
    override fun onFlush() {
        if (inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET) {
            dsp = SoundDsp(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
            meter = SmartLoudness(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
            frame = FloatArray(inputAudioFormat.channelCount)
            lastEffects = null; lastGain = Float.NaN
            processedFrames = 0
        }
    }
    override fun onReset() { dsp = null; meter = null; frame = FloatArray(0) }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val processor = dsp ?: return
        val count = inputAudioFormat.channelCount
        val size = inputBuffer.remaining()
        require(size % (count * 2) == 0) { "PCM buffer contains a partial frame" }
        val output = replaceOutputBuffer(size)
        val effects = AudioSettings.playbackEffects
        val mode = AudioSettings.playbackLoudness
        var gain = if (mode == LoudnessMode.SMART) meter?.gainDb ?: 0f else replayGain.gainFor(mode, albumPlayback)
        if (lastEffects != effects || lastGain != gain) {
            processor.configure(effects, gain); lastEffects = effects; lastGain = gain
        }
        while (inputBuffer.hasRemaining()) {
            for (ch in 0 until count) frame[ch] = inputBuffer.short / 32768f
            if (mode == LoudnessMode.SMART) {
                meter?.observe(frame)
                val updated = meter?.gainDb ?: 0f
                if (updated != gain) { gain = updated; processor.configure(effects, gain); lastGain = gain }
            }
            processor.process(frame)
            for (ch in 0 until count) output.putShort((frame[ch] * 32768f).roundToInt().coerceIn(-32768, 32767).toShort())
        }
        processedFrames += size / (count * 2)
        output.flip()
    }
}
