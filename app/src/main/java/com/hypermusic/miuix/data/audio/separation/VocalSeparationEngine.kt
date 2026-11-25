package com.hypermusic.miuix.data.audio.separation

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface VocalSeparationStatus {
    data object Idle : VocalSeparationStatus
    data object MissingModel : VocalSeparationStatus
    data class Preparing(val progress: Float, val stage: String) : VocalSeparationStatus
    data class Ready(val entry: VocalSeparationCacheEntry) : VocalSeparationStatus
    data class Failed(val message: String) : VocalSeparationStatus
}

data class VocalSeparationUiState(
    val enabled: Boolean = false,
    val model: VocalSeparationModel = VocalSeparationModel.DEFAULT,
    val hardwareAcceleration: Boolean = true,
    val mix: VocalSeparationMix = VocalSeparationMix.ORIGINAL,
    val trackKey: String = "",
    val trackTitle: String = "",
    val trackArtist: String = "",
    val sourceAvailable: Boolean = false,
    val status: VocalSeparationStatus = VocalSeparationStatus.Idle,
) {
    val mixing: Boolean
        get() = enabled && status is VocalSeparationStatus.Ready
}

/**
 * 人声分离协调器：
 * - 跟随播放器当前歌曲准备分离音轨；
 * - 以 44.1 kHz PCM 缓存伴奏/人声；
 * - 在 ExoPlayer 音频线程按播放进度把人声与伴奏重新混合，并实时调整人声增益。
 */
object VocalSeparationEngine {
    private const val TAG = "VocalSeparationEngine"
    private const val BLOCK_FRAMES = 32_768L

    private var context: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val separatorMutex = Mutex()
    private var currentJob: Job? = null
    private var lastPreparedKey = ""

    private data class CurrentTrack(
        val key: String,
        val title: String,
        val artist: String,
        val album: String,
        val sourceUrl: String,
        val cacheKey: String?,
        val headers: Map<String, String>,
    )

    private data class StemEntry(
        val id: String,
        val vocalsFile: File,
        val instFile: File,
        val totalFrames: Long,
    )

    private val mutableUi = MutableStateFlow(VocalSeparationUiState())
    val uiState = mutableUi.asStateFlow()
    private var currentTrack: CurrentTrack? = null
    private var cache: VocalSeparationCache? = null
    @Volatile private var activeEntry: StemEntry? = null

    @Volatile private var activeMixing: Boolean = false
    internal val isMixing: Boolean get() = activeMixing
    @Volatile private var activeMix: VocalSeparationMix = VocalSeparationMix.ORIGINAL

    // ---- 音频线程独占状态 ----
    private var lastMixedEntry: StemEntry? = null
    private var stemMixWeight: Float = 0f
    private var blockReader: DualStemBlockReader? = null

    fun init(context: Context) {
        if (this.context != null) return
        synchronized(this) {
            if (this.context != null) return
            val appContext = context.applicationContext
            this.context = appContext
            VocalSeparationSettings.init(appContext)
            VocalSeparationModelStore.init(appContext)
            cache = VocalSeparationCache(appContext)
            val config = VocalSeparationSettings.value
            activeMix = config.mix
            mutableUi.value = mutableUi.value.copy(
                enabled = config.enabled,
                model = config.model,
                hardwareAcceleration = config.hardwareAcceleration,
                mix = config.mix,
            )
            scope.launch {
                VocalSeparationSettings.state.collect { value ->
                    val old = mutableUi.value
                    mutableUi.value = old.copy(
                        enabled = value.enabled,
                        model = value.model,
                        hardwareAcceleration = value.hardwareAcceleration,
                        mix = value.mix,
                    )
                    if (old.model != value.model || old.enabled != value.enabled) {
                        refreshPreparation(force = true)
                    }
                    syncAudioFlags()
                }
            }
            scope.launch {
                VocalSeparationModelStore.states.collect { states ->
                    val model = VocalSeparationSettings.value.model
                    if (states[model.id] is VocalModelDownloadState.Ready) {
                        if (mutableUi.value.status is VocalSeparationStatus.MissingModel) {
                            refreshPreparation(force = false)
                        }
                    } else {
                        VocalSeparationModel.entries.firstOrNull {
                            states[it.id] is VocalModelDownloadState.Ready
                        }?.let(VocalSeparationSettings::setModel)
                    }
                }
            }
            VocalSeparationModelStore.refresh()
            scope.launch(Dispatchers.IO) {
                // 进程被系统回收时来不及清理的临时音频，在下次启动时统一清掉。
                java.io.File(appContext.cacheDir, "vocal_separation_tmp").deleteRecursively()
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        VocalSeparationSettings.setEnabled(enabled)
    }

    fun setModel(model: VocalSeparationModel) {
        VocalSeparationSettings.setModel(model)
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        VocalSeparationSettings.setHardwareAcceleration(enabled)
    }

    fun setMix(mix: VocalSeparationMix) {
        VocalSeparationSettings.setMix(mix)
        activeMix = mix
        mutableUi.value = mutableUi.value.copy(mix = mix)
    }

    fun notifyTrackChanged(
        trackKey: String,
        title: String,
        artist: String,
        album: String,
        sourceUrl: String,
        cacheKey: String? = null,
        headers: Map<String, String> = emptyMap(),
    ) {
        if (context == null) return
        val track = CurrentTrack(trackKey, title, artist, album, sourceUrl, cacheKey, headers)
        val changed = currentTrack?.key != trackKey || currentTrack?.sourceUrl != sourceUrl ||
            currentTrack?.cacheKey != cacheKey
        if (changed) {
            currentJob?.cancel()
            currentJob = null
            lastPreparedKey = ""
            clearAudioEntry()
        }
        currentTrack = track
        mutableUi.value = mutableUi.value.copy(
            trackKey = trackKey,
            trackTitle = title,
            trackArtist = artist,
            sourceAvailable = sourceUrl.isNotBlank(),
        )
        if (changed) refreshPreparation(force = false)
    }

    fun clearTrack() {
        currentJob?.cancel()
        currentJob = null
        lastPreparedKey = ""
        currentTrack = null
        clearAudioEntry()
        mutableUi.value = mutableUi.value.copy(
            trackKey = "",
            trackTitle = "",
            trackArtist = "",
            sourceAvailable = false,
            status = VocalSeparationStatus.Idle,
        )
        syncAudioFlags()
    }

    fun onSinkFlush() {
        lastMixedEntry = null
        stemMixWeight = 0f
    }

    fun retry() {
        lastPreparedKey = ""
        refreshPreparation(force = true)
    }

    fun refreshModels() {
        VocalSeparationModelStore.refresh()
        lastPreparedKey = ""
        refreshPreparation(force = true)
    }

    fun listCacheEntries(): List<VocalSeparationCacheEntry> = cache?.listEntries() ?: emptyList()

    suspend fun trimCacheToBudget() {
        val currentId = activeEntry?.id
        withContext(Dispatchers.IO) { cache?.trimToBudget(keepId = currentId) }
    }

    suspend fun deleteCacheEntries(ids: Set<String>): Boolean {
        val deleted = withContext(Dispatchers.IO) {
            ids.filter { cache?.deleteEntry(it) == true }
        }
        if (activeEntry?.id in deleted) {
            clearAudioEntry()
            lastPreparedKey = ""
            mutableUi.value = mutableUi.value.copy(status = VocalSeparationStatus.Idle)
            syncAudioFlags()
        }
        return deleted.size == ids.size
    }

    suspend fun clearCache(): Boolean {
        currentJob?.cancel()
        currentJob = null
        lastPreparedKey = ""
        clearAudioEntry()
        mutableUi.value = mutableUi.value.copy(status = VocalSeparationStatus.Idle)
        syncAudioFlags()
        return separatorMutex.withLock {
            withContext(Dispatchers.IO) { cache?.clearAll() == true }
        }
    }

    private fun refreshPreparation(force: Boolean) {
        val cfg = VocalSeparationSettings.value
        val track = currentTrack
        if (!cfg.enabled || track == null || track.sourceUrl.isBlank()) {
            currentJob?.cancel()
            currentJob = null
            lastPreparedKey = ""
            clearAudioEntry()
            mutableUi.value = mutableUi.value.copy(
                status = if (!cfg.enabled) VocalSeparationStatus.Idle else VocalSeparationStatus.MissingModel,
            )
            syncAudioFlags()
            return
        }

        val cached = cache?.findEntry(track.key, cfg.model.id)
        if (cached != null) {
            currentJob?.cancel()
            currentJob = null
            attachEntry(cached)
            mutableUi.value = mutableUi.value.copy(status = VocalSeparationStatus.Ready(cached))
            syncAudioFlags()
            return
        }

        clearAudioEntry()
        val appContext = context ?: return
        if (!VocalSeparationModelStore.isReady(cfg.model, appContext)) {
            currentJob?.cancel()
            currentJob = null
            mutableUi.value = mutableUi.value.copy(status = VocalSeparationStatus.MissingModel)
            syncAudioFlags()
            return
        }

        val jobKey = "${track.key}|${cfg.model.id}|${track.cacheKey}|${track.sourceUrl}"
        if (!force && currentJob?.isActive == true && lastPreparedKey == jobKey) return
        lastPreparedKey = jobKey
        currentJob?.cancel()
        mutableUi.value = mutableUi.value.copy(
            status = VocalSeparationStatus.Preparing(0f, "准备分离"),
        )
        syncAudioFlags()
        currentJob = scope.launch {
            val job = coroutineContext[Job]
            try {
                val entry = separatorMutex.withLock {
                    VocalSeparationSeparator.separateTrack(
                        context = appContext,
                        cache = cache ?: error("缓存未初始化"),
                        model = cfg.model,
                        songKey = track.key,
                        sourceUrl = track.sourceUrl,
                        cacheKey = track.cacheKey,
                        headers = track.headers,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        onProgress = { progress ->
                            scope.launch {
                                if (currentJob === job && job?.isActive == true &&
                                    mutableUi.value.status is VocalSeparationStatus.Preparing
                                ) {
                                    mutableUi.value = mutableUi.value.copy(
                                        status = VocalSeparationStatus.Preparing(
                                            progress,
                                            when {
                                                progress < 0.08f -> "读取音频"
                                                progress < 0.18f -> "解码音频"
                                                progress >= 0.98f -> "保存分离结果"
                                                else -> "AI 分离中"
                                            },
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
                if (currentJob === job) {
                    attachEntry(entry)
                    mutableUi.value = mutableUi.value.copy(status = VocalSeparationStatus.Ready(entry))
                    syncAudioFlags()
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                if (currentJob === job) {
                    mutableUi.value = mutableUi.value.copy(
                        status = VocalSeparationStatus.Failed(t.message ?: "人声分离失败"),
                    )
                    syncAudioFlags()
                }
            }
        }
    }

    private fun attachEntry(entry: VocalSeparationCacheEntry) {
        val cache = cache ?: return
        synchronized(this) {
            if (activeEntry?.id == entry.id) return
            blockReader?.close()
            blockReader = null
            activeEntry = StemEntry(
                id = entry.id,
                vocalsFile = cache.vocalsFile(entry.id),
                instFile = cache.instrumentalFile(entry.id),
                totalFrames = entry.totalFrames,
            )
        }
    }

    private fun clearAudioEntry() {
        synchronized(this) {
            activeMixing = false
            blockReader?.close()
            blockReader = null
            activeEntry = null
        }
    }

    private fun syncAudioFlags() {
        activeMixing = mutableUi.value.enabled && activeEntry != null
        activeMix = mutableUi.value.mix
    }

    /**
     * 在 ExoPlayer 音频线程中调用，原地改写 PCM 缓冲区。
     * [mediaTimeUs] 为首个 PCM 帧在当前音轨中的时间戳。
     */
    fun processAudioChunk(
        chunk: ByteBuffer,
        encoding: Int,
        channels: Int,
        sampleRate: Int,
        mediaTimeUs: Long,
    ) {
        if (!activeMixing) return
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_16BIT_BIG_ENDIAN) return
        val totalFrames = chunk.remaining() / (channels.coerceAtLeast(1) * 2)
        if (totalFrames <= 0) return

        val entry = activeEntry ?: return
        if (lastMixedEntry !== entry) {
            lastMixedEntry = entry
            stemMixWeight = 0f
        }

        val songPosition = mediaTimeUs.toDouble() * VocalSeparationCache.SAMPLE_RATE / 1_000_000.0
        val stepPerFrame = VocalSeparationCache.SAMPLE_RATE.toDouble() / sampleRate.coerceAtLeast(1)
        val maxNeeded = (songPosition + totalFrames * stepPerFrame + 2.0).toLong()
        if (songPosition >= entry.totalFrames || maxNeeded <= 0L) return
        val window = try {
            synchronized(this) {
                if (!activeMixing || activeEntry !== entry) {
                    null
                } else {
                    ensureReader(entry)?.ensureWindow(songPosition.toLong().coerceAtLeast(0L), maxNeeded)
                }
            }
        } catch (error: IOException) {
            Log.w(TAG, "Failed to read separated tracks", error)
            synchronized(this) {
                if (activeEntry === entry) {
                    activeMixing = false
                    blockReader?.close()
                    blockReader = null
                }
            }
            scope.launch {
                if (activeEntry === entry) {
                    clearAudioEntry()
                    mutableUi.value = mutableUi.value.copy(
                        status = VocalSeparationStatus.Failed("无法读取分离音轨"),
                    )
                }
            }
            null
        } ?: return

        val rampStep = (totalFrames.toFloat() / (sampleRate * 0.025f).coerceAtLeast(1f)).coerceIn(0f, 1f)
        stemMixWeight = (stemMixWeight + rampStep).coerceAtMost(1f)

        mixIntoBuffer(
            buffer = chunk,
            vocals = window.vocals,
            instrumental = window.inst,
            windowStartFrame = window.startFrame,
            windowFrameCount = window.frameCount,
            songPositionSamples = songPosition,
            stepPerFrame = stepPerFrame,
            stemGains = activeMix,
            mixWeight = stemMixWeight,
            channels = channels,
            bigEndian = encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN,
        )
    }

    private fun mixIntoBuffer(
        buffer: ByteBuffer,
        vocals: ShortArray,
        instrumental: ShortArray,
        windowStartFrame: Long,
        windowFrameCount: Int,
        songPositionSamples: Double,
        stepPerFrame: Double,
        stemGains: VocalSeparationMix,
        mixWeight: Float,
        channels: Int,
        bigEndian: Boolean,
    ) {
        val sampleBytes = 2
        val frameBytes = sampleBytes * channels.coerceAtLeast(1)
        val totalFrames = buffer.remaining() / frameBytes
        if (totalFrames <= 0) return
        val oldOrder = buffer.order()
        val basePosition = buffer.position()
        buffer.order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        val maxWindowFrames = minOf(vocals.size / 2, instrumental.size / 2, windowFrameCount)
        val mix = mixWeight.coerceIn(0f, 1f)
        val originalWeight = 1f - mix

        for (frame in 0 until totalFrames) {
            val absolute = songPositionSamples + frame * stepPerFrame
            val relative = absolute - windowStartFrame.toDouble()
            val index0 = floor(relative).toInt()
            val fraction = (relative - index0).toFloat().coerceIn(0f, 1f)
            if (index0 < 0 || index0 >= maxWindowFrames) continue
            val index1 = (index0 + 1).coerceAtMost(maxWindowFrames - 1)
            val p0 = index0 * 2
            val p1 = index1 * 2
            val vL = (vocals[p0] + (vocals[p1] - vocals[p0]) * fraction) / 32768f
            val vR = (vocals[p0 + 1] + (vocals[p1 + 1] - vocals[p0 + 1]) * fraction) / 32768f
            val iL = (instrumental[p0] + (instrumental[p1] - instrumental[p0]) * fraction) / 32768f
            val iR = (instrumental[p0 + 1] + (instrumental[p1 + 1] - instrumental[p0 + 1]) * fraction) / 32768f
            val stemL = softClip(iL * stemGains.instrumentalGain + vL * stemGains.vocalGain)
            val stemR = softClip(iR * stemGains.instrumentalGain + vR * stemGains.vocalGain)
            val frameBase = basePosition + frame * frameBytes
            for (channel in 0 until channels.coerceAtLeast(1)) {
                val position = frameBase + channel * 2
                val stemSample = when {
                    channels == 1 -> (stemL + stemR) * 0.5f
                    channel % 2 == 0 -> stemL
                    else -> stemR
                }
                val mixed = if (originalWeight > 0f) {
                    val original = buffer.getShort(position) / 32768f
                    softClip(original * originalWeight + stemSample * mix)
                } else {
                    stemSample
                }
                buffer.putShort(position, (mixed * 32767f).roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
        buffer.order(oldOrder)
    }

    private fun softClip(value: Float): Float =
        if (abs(value) <= 0.98f) value
        else (0.98f * sign(value) + 0.02f * tanh(((value - 0.98f * sign(value)) / 0.02f).toDouble()).toFloat())
            .coerceIn(-1f, 1f)

    private fun ensureReader(entry: StemEntry): DualStemBlockReader? {
        blockReader?.let { if (it.entryId == entry.id) return it }
        return synchronized(this) {
            blockReader?.let { if (it.entryId == entry.id) return@synchronized it }
            blockReader?.close()
            DualStemBlockReader(entry.id, entry.vocalsFile, entry.instFile, entry.totalFrames).also {
                blockReader = it
            }
        }
    }

    private class StemWindow(val startFrame: Long, val frameCount: Int, val vocals: ShortArray, val inst: ShortArray)

    private class DualStemBlockReader(
        val entryId: String,
        vocalsFile: File,
        instFile: File,
        private val totalFrames: Long,
    ) {
        private val vocalsRaf = RandomAccessFile(vocalsFile, "r")
        private val instRaf = try {
            RandomAccessFile(instFile, "r")
        } catch (error: IOException) {
            runCatching { vocalsRaf.close() }
            throw error
        }
        private var windowStart = -1L
        private var windowFrameCount = 0
        private var vocalsBuffer = ShortArray(0)
        private var instBuffer = ShortArray(0)
        private var rawBytes = ByteArray(0)

        fun ensureWindow(fromFrame: Long, toFrame: Long): StemWindow? {
            if (totalFrames <= 0L) return null
            val clampedFrom = fromFrame.coerceIn(0L, totalFrames - 1L)
            val clampedTo = toFrame.coerceIn(clampedFrom + 1L, totalFrames)
            if (windowStart >= 0L && clampedFrom >= windowStart && clampedTo <= windowStart + windowFrameCount) {
                return StemWindow(windowStart, windowFrameCount, vocalsBuffer, instBuffer)
            }
            val needed = maxOf(BLOCK_FRAMES, clampedTo - clampedFrom)
            val available = (totalFrames - clampedFrom).coerceIn(0L, needed).toInt()
            if (available <= 0) return null
            val byteLength = available * 4
            if (rawBytes.size < byteLength) rawBytes = ByteArray(byteLength)
            val sampleLength = available * 2
            if (vocalsBuffer.size < sampleLength) vocalsBuffer = ShortArray(sampleLength)
            if (instBuffer.size < sampleLength) instBuffer = ShortArray(sampleLength)

            val offset = clampedFrom * 4L
            vocalsRaf.seek(offset)
            readFullyOrZero(vocalsRaf, rawBytes, byteLength)
            ByteBuffer.wrap(rawBytes, 0, byteLength).order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().get(vocalsBuffer, 0, sampleLength)

            instRaf.seek(offset)
            readFullyOrZero(instRaf, rawBytes, byteLength)
            ByteBuffer.wrap(rawBytes, 0, byteLength).order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().get(instBuffer, 0, sampleLength)

            windowStart = clampedFrom
            windowFrameCount = available
            return StemWindow(windowStart, available, vocalsBuffer, instBuffer)
        }

        private fun readFullyOrZero(raf: RandomAccessFile, target: ByteArray, length: Int) {
            var total = 0
            while (total < length) {
                val read = raf.read(target, total, length - total)
                if (read < 0) break
                total += read
            }
            if (total < length) target.fill(0, total, length)
        }

        fun close() {
            runCatching { vocalsRaf.close() }
            runCatching { instRaf.close() }
        }
    }
}
