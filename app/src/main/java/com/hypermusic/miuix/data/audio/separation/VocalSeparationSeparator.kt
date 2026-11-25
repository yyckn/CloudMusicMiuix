package com.hypermusic.miuix.data.audio.separation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import com.hypermusic.miuix.data.cache.AudioCache
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object VocalSeparationSeparator {
    private const val TAG = "VocalSeparation"
    private const val DECODER_TIMEOUT_US = 2_000L
    private const val ENCODING_PCM_24BIT_PACKED = 21
    private const val ENCODING_PCM_32BIT = 22
    private const val VULKAN_1_1 = (1 shl 22) or (1 shl 12)
    private val snapdragonModel = Regex("SM[0-9]{4}(?![0-9])", RegexOption.IGNORE_CASE)
    private val adrenoWebGpuModels = setOf(
        "SM8475", // 8+ Gen 1
        "SM8550", // 8 Gen 2
        "SM8650", "SM8635", // 8 Gen 3, 8s Gen 3
        "SM8750", "SM8735", // 8 Elite, 8s Gen 4
        "SM8850", "SM8845", // 8 Elite Gen 5, 8 Gen 5
        "SM8950", "SM8975", // 8 Elite Gen 6, 8 Elite Extreme Gen 6
        "SM7475", "SM7675", // 7+ Gen 2 / Gen 3
        "SM7550", "SM7750", // 7 Gen 3 / Gen 4
        "SM7435", "SM7635", // 7s Gen 2 / Gen 3 / Gen 4
    )

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun separateTrack(
        context: Context,
        cache: VocalSeparationCache,
        model: VocalSeparationModel,
        songKey: String,
        sourceUrl: String,
        cacheKey: String?,
        headers: Map<String, String>,
        title: String,
        artist: String,
        album: String,
        onBackendSelected: (String) -> Unit = {},
        onProgress: (Float) -> Unit,
    ): VocalSeparationCacheEntry = withContext(Dispatchers.IO) {
        if (!VocalSeparationModelStore.isReady(model, context)) {
            error("${model.label} 模型尚未下载")
        }

        cache.findEntry(songKey, model.id)?.let {
            onProgress(1f)
            return@withContext it
        }

        val workDir = File(context.cacheDir, "vocal_separation_tmp").apply { mkdirs() }
        var downloadedFile: File? = null
        val decoded = File.createTempFile("decoded_44k_", ".f32", workDir)
        val vocalsTemp = File.createTempFile("vocals_", ".pcm", workDir)
        val instTemp = File.createTempFile("instrumental_", ".pcm", workDir)

        try {
            onProgress(0.02f)
            val source = resolveSource(
                context = context,
                sourceUrl = sourceUrl,
                cacheKey = cacheKey,
                headers = headers,
                workDir = workDir,
                onDownloaded = { downloadedFile = it },
                onDownloadProgress = { fraction -> onProgress(0.02f + fraction * 0.06f) },
            )
            currentCoroutineContext().ensureActive()

            onProgress(0.08f)
            val totalFrames = decodeToStereo44100(context, source, decoded) { fraction ->
                onProgress((0.08f + fraction * 0.10f).coerceIn(0.08f, 0.18f))
            }
            if (totalFrames <= 0) error("无法解码音频轨道")
            currentCoroutineContext().ensureActive()

            val started = SystemClock.elapsedRealtime()
            val backends = inferenceBackends(context)
            var completedBackend = ""
            for (backend in backends) {
                currentCoroutineContext().ensureActive()
                try {
                    createSession(context, model, backend).use { session ->
                        onBackendSelected(backend.label)
                        Log.i(TAG, "separating \"$title\" with ${model.id} on ${backend.label}")
                        Pcm16Writer(vocalsTemp).use { vocalsWriter ->
                            Pcm16Writer(instTemp).use { instWriter ->
                                separateMdx(session, model, decoded, totalFrames, vocalsWriter, instWriter, onProgress)
                            }
                        }
                    }
                    completedBackend = backend.label
                    break
                } catch (error: OrtException) {
                    if (backend == InferenceBackend.CPU) throw error
                    Log.w(TAG, "${backend.label} failed, retrying with the next backend", error)
                }
            }
            Log.i(TAG, "separated in ${SystemClock.elapsedRealtime() - started}ms")

            onProgress(0.98f)
            currentCoroutineContext().ensureActive()
            cache.commitEntry(
                songKey = songKey,
                title = title,
                artist = artist,
                album = album,
                modelId = model.id,
                totalFrames = totalFrames,
                durationMs = totalFrames.toLong() * 1000L / VocalSeparationCache.SAMPLE_RATE,
                vocalsTemp = vocalsTemp,
                instTemp = instTemp,
                backend = completedBackend,
            ).also { onProgress(1f) }
        } catch (cancel: CancellationException) {
            vocalsTemp.delete()
            instTemp.delete()
            throw cancel
        } catch (t: Throwable) {
            vocalsTemp.delete()
            instTemp.delete()
            Log.e(TAG, "separation failed", t)
            throw t
        } finally {
            decoded.delete()
            downloadedFile?.delete()
        }
    }

    internal enum class InferenceBackend(val label: String) {
        WEBGPU("GPU · WebGPU"),
        NNAPI("NNAPI"),
        CPU("CPU"),
    }

    private fun inferenceBackends(context: Context): List<InferenceBackend> {
        if (!VocalSeparationSettings.value.hardwareAcceleration) return listOf(InferenceBackend.CPU)
        return inferenceBackends(
            socModel = Build.SOC_MODEL,
            hasVulkan = context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_VULKAN_HARDWARE_VERSION,
                VULKAN_1_1,
            ),
            available = OrtEnvironment.getAvailableProviders(),
        )
    }

    internal fun inferenceBackends(
        socModel: String,
        hasVulkan: Boolean,
        available: Set<OrtProvider>,
    ): List<InferenceBackend> = buildList {
        val model = snapdragonModel.matchAt(socModel.trim(), 0)?.value?.uppercase()
        if (hasVulkan && model in adrenoWebGpuModels && OrtProvider.WEBGPU in available) {
            add(InferenceBackend.WEBGPU)
        }
        if (OrtProvider.NNAPI in available) add(InferenceBackend.NNAPI)
        add(InferenceBackend.CPU)
    }

    private fun createSession(context: Context, model: VocalSeparationModel, backend: InferenceBackend): OrtSession {
        val env = OrtEnvironment.getEnvironment()
        val modelFile = VocalSeparationModelStore.file(model, context)
        return OrtSession.SessionOptions().use { options ->
            val maxThreads = if (backend == InferenceBackend.CPU) 8 else 6
            options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, maxThreads))
            when (backend) {
                InferenceBackend.WEBGPU -> options.addWebGPU(emptyMap())
                InferenceBackend.NNAPI -> options.addNnapi()
                InferenceBackend.CPU -> Unit
            }
            env.createSession(modelFile.absolutePath, options)
        }
    }

    private suspend fun separateMdx(
        session: OrtSession,
        model: VocalSeparationModel,
        decodedFile: File,
        totalFrames: Int,
        vocalsWriter: Pcm16Writer,
        instWriter: Pcm16Writer,
        onProgress: (Float) -> Unit,
    ) {
        val env = OrtEnvironment.getEnvironment()
        val stft = MdxStftProcessor(model.nFft, model.hopLength, model.dimF, model.dimT)
        val chunkSize = stft.chunkSize
        val trim = stft.trim
        val genSize = stft.genSize.coerceAtLeast(1)
        val totalChunks = ((totalFrames + genSize - 1) / genSize).coerceAtLeast(1)
        val leftMix = FloatArray(chunkSize)
        val rightMix = FloatArray(chunkSize)
        val predLeft = FloatArray(chunkSize)
        val predRight = FloatArray(chunkSize)
        val tensorSize = 4 * model.dimF * model.dimT
        val inputScratch = FloatArray(tensorSize)
        val inputBuffer = directFloatBuffer(tensorSize)
        val outputBuffer = directFloatBuffer(tensorSize)
        val inputShape = longArrayOf(1L, 4L, model.dimF.toLong(), model.dimT.toLong())
        val outputScratch = FloatArray(tensorSize)
        val byteScratch = ByteArray(chunkSize * 8)
        val inputName = session.inputNames.first()
        val outputName = session.outputNames.first()
        val compensate = model.compensate
        val vocalsIsPrimary = model.vocalsIsPrimary

        var inferenceMs = 0L
        OnnxTensor.createTensor(env, inputBuffer, inputShape).use { inputTensor ->
            OnnxTensor.createTensor(env, outputBuffer, inputShape).use { outputTensor ->
                val inputs = mapOf(inputName to inputTensor)
                val outputs = mapOf(outputName to outputTensor)
                RandomAccessFile(decodedFile, "r").use { raf ->
                    for (chunkIndex in 0 until totalChunks) {
                        currentCoroutineContext().ensureActive()
                        val songStart = chunkIndex * genSize
                        val emitCount = minOf(genSize, totalFrames - songStart)
                        if (emitCount <= 0) break

                        readWindow(
                            raf = raf,
                            windowStartFrame = songStart - trim,
                            windowFrames = chunkSize,
                            totalSongFrames = totalFrames,
                            bytes = byteScratch,
                            leftOut = leftMix,
                            rightOut = rightMix,
                        )

                        stft.forwardStereo(leftMix, rightMix, inputScratch)
                        inputBuffer.rewind()
                        inputBuffer.put(inputScratch)
                        inputBuffer.rewind()

                        val inferenceStart = SystemClock.elapsedRealtime()
                        session.run(inputs, outputs).close()
                        inferenceMs += SystemClock.elapsedRealtime() - inferenceStart
                        outputBuffer.rewind()
                        outputBuffer.get(outputScratch)

                        stft.inverseStereo(outputScratch, predLeft, predRight)

                        for (i in 0 until emitCount) {
                            val center = trim + i
                            val primL = predLeft[center] * compensate
                            val primR = predRight[center] * compensate
                            val secondaryL = leftMix[center] - primL
                            val secondaryR = rightMix[center] - primR
                            if (vocalsIsPrimary) {
                                vocalsWriter.write(floatToPcm16(primL), floatToPcm16(primR))
                                instWriter.write(floatToPcm16(secondaryL), floatToPcm16(secondaryR))
                            } else {
                                vocalsWriter.write(floatToPcm16(secondaryL), floatToPcm16(secondaryR))
                                instWriter.write(floatToPcm16(primL), floatToPcm16(primR))
                            }
                        }

                        val progress = 0.18f + 0.78f * ((chunkIndex + 1).toFloat() / totalChunks.toFloat())
                        onProgress(progress.coerceIn(0.18f, 0.96f))
                    }
                }
            }
        }
        Log.i(TAG, "model inference: ${inferenceMs}ms across $totalChunks chunks")
    }

    private fun directFloatBuffer(size: Int): FloatBuffer =
        ByteBuffer.allocateDirect(size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun readWindow(
        raf: RandomAccessFile,
        windowStartFrame: Int,
        windowFrames: Int,
        totalSongFrames: Int,
        bytes: ByteArray,
        leftOut: FloatArray,
        rightOut: FloatArray,
    ) {
        val validStart = windowStartFrame.coerceAtLeast(0)
        val validEnd = (windowStartFrame + windowFrames).coerceAtMost(totalSongFrames)
        if (validStart >= validEnd) {
            leftOut.fill(0f)
            rightOut.fill(0f)
            return
        }
        val dstOffset = validStart - windowStartFrame
        val validCount = validEnd - validStart
        if (dstOffset > 0) {
            leftOut.fill(0f, 0, dstOffset)
            rightOut.fill(0f, 0, dstOffset)
        }
        val byteLen = validCount * 8
        raf.seek(validStart.toLong() * 8L)
        var totalRead = 0
        while (totalRead < byteLen) {
            val read = raf.read(bytes, totalRead, byteLen - totalRead)
            if (read < 0) break
            totalRead += read
        }
        val floatBuffer = ByteBuffer.wrap(bytes, 0, byteLen).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        for (i in 0 until validCount) {
            leftOut[dstOffset + i] = floatBuffer.get(i * 2)
            rightOut[dstOffset + i] = floatBuffer.get(i * 2 + 1)
        }
        val tail = dstOffset + validCount
        if (tail < windowFrames) {
            leftOut.fill(0f, tail, windowFrames)
            rightOut.fill(0f, tail, windowFrames)
        }
    }

    private fun resolveSource(
        context: Context,
        sourceUrl: String,
        cacheKey: String?,
        headers: Map<String, String>,
        workDir: File,
        onDownloaded: (File) -> Unit,
        onDownloadProgress: (Float) -> Unit,
    ): Uri {
        if (!cacheKey.isNullOrBlank()) {
            val temp = File.createTempFile("separation_cached_", ".audio", workDir)
            onDownloaded(temp)
            copyCachedAudio(context, cacheKey, temp, onDownloadProgress)
            return Uri.fromFile(temp)
        }
        val uri = Uri.parse(sourceUrl)
        if (uri.scheme == "file" || uri.scheme == "content") return uri
        if (uri.scheme == "http" || uri.scheme == "https") {
            val temp = File.createTempFile("separation_source_", ".audio", workDir)
            onDownloaded(temp)
            val builder = Request.Builder().url(sourceUrl)
            headers.forEach { (key, value) -> builder.header(key, value) }
            httpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) error("音频下载失败（HTTP ${response.code}）")
                val body = response.body ?: error("音频响应为空")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0L) onDownloadProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            return Uri.fromFile(temp)
        }
        error("当前音频来源不支持人声分离")
    }

    private fun copyCachedAudio(
        context: Context,
        cacheKey: String,
        target: File,
        onProgress: (Float) -> Unit,
    ) {
        val cache = AudioCache.get(context)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey))
        if (length <= 0L || !cache.isCached(cacheKey, 0L, length)) {
            error("当前歌曲尚未完整缓存，请先完整播放或下载后再试")
        }
        val factory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(FileDataSource.Factory())
        val source = factory.createDataSource()
        try {
            val spec = DataSpec.Builder()
                .setUri(Uri.parse("file:///vocal-separation-cache"))
                .setKey(cacheKey)
                .setLength(length)
                .build()
            source.open(spec)
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(256 * 1024)
                var written = 0L
                while (written < length) {
                    val read = source.read(buffer, 0, minOf(buffer.size.toLong(), length - written).toInt())
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    written += read
                    onProgress((written.toFloat() / length).coerceIn(0f, 1f))
                }
                if (written < length) error("音频缓存读取不完整，请重新完整播放后再试")
            }
        } finally {
            runCatching { source.close() }
        }
    }

    private fun decodeToStereo44100(
        context: Context,
        source: Uri,
        outFile: File,
        onProgress: (Float) -> Unit,
    ): Int {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var sink: FloatStereoWriter? = null
        try {
            if (source.scheme == "file") {
                extractor.setDataSource(source.path ?: error("无效文件路径"))
            } else {
                extractor.setDataSource(context, source, null)
            }
            var trackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    trackIndex = i
                    trackFormat = format
                    break
                }
            }
            if (trackIndex < 0 || trackFormat == null) error("未找到音频轨道")
            extractor.selectTrack(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                trackFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = if (trackFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                trackFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else AudioFormat.ENCODING_PCM_16BIT
            var resampler = StereoResampler(sampleRate, channels)
            sink = FloatStereoWriter(outFile)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(trackFormat, null, null, 0)
                start()
            }
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var shortScratch = ShortArray(8192)
            var lastProgress = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DECODER_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, DECODER_TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        val rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        } else sampleRate
                        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        } else channels
                        encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                        if (rate != sampleRate || channelCount != channels) {
                            sampleRate = rate
                            channels = channelCount
                            resampler = StereoResampler(sampleRate, channels)
                        }
                    }
                    outputIndex >= 0 -> {
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                        if (info.size > 0) {
                            val output = codec.getOutputBuffer(outputIndex)
                            if (output != null) {
                                output.position(info.offset)
                                output.limit(info.offset + info.size)
                                output.order(ByteOrder.LITTLE_ENDIAN)
                                val sampleCount = when (encoding) {
                                    AudioFormat.ENCODING_PCM_FLOAT, ENCODING_PCM_32BIT -> info.size / 4
                                    ENCODING_PCM_24BIT_PACKED -> info.size / 3
                                    AudioFormat.ENCODING_PCM_8BIT -> info.size
                                    else -> info.size / 2
                                }
                                if (shortScratch.size < sampleCount) shortScratch = ShortArray(sampleCount)
                                when (encoding) {
                                    AudioFormat.ENCODING_PCM_FLOAT -> {
                                        val fb = output.asFloatBuffer()
                                        for (sample in 0 until sampleCount) {
                                            shortScratch[sample] = floatToPcm16(fb.get(sample))
                                        }
                                    }
                                    ENCODING_PCM_32BIT -> {
                                        val ib = output.asIntBuffer()
                                        for (sample in 0 until sampleCount) {
                                            shortScratch[sample] = (ib.get(sample) shr 16).toShort()
                                        }
                                    }
                                    ENCODING_PCM_24BIT_PACKED -> {
                                        var position = info.offset
                                        for (sample in 0 until sampleCount) {
                                            val low = output.get(position + 1).toInt() and 0xFF
                                            val high = output.get(position + 2).toInt()
                                            shortScratch[sample] = ((high shl 8) or low).toShort()
                                            position += 3
                                        }
                                    }
                                    AudioFormat.ENCODING_PCM_8BIT -> {
                                        for (sample in 0 until sampleCount) {
                                            shortScratch[sample] =
                                                (((output.get(info.offset + sample).toInt() and 0xFF) - 128) shl 8).toShort()
                                        }
                                    }
                                    else -> output.asShortBuffer().get(shortScratch, 0, sampleCount)
                                }
                                resampler.feed(shortScratch, sampleCount, sink)
                            }
                        }
                        if (durationUs > 0L) {
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastProgress >= 120L) {
                                lastProgress = now
                                onProgress((info.presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            resampler.finish(sink)
            return sink.totalFrames
        } finally {
            runCatching { sink?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private class StereoResampler(
        private val sourceRate: Int,
        private val sourceChannels: Int,
    ) {
        private var previousLeft = 0f
        private var previousRight = 0f
        private var nextSourcePosition = 0.0
        private var inputFrames = 0L
        private val step = sourceRate.coerceAtLeast(1).toDouble() / VocalSeparationCache.SAMPLE_RATE.toDouble()

        fun feed(pcm: ShortArray, sampleCount: Int, sink: FloatStereoWriter) {
            val channels = sourceChannels.coerceAtLeast(1)
            val frames = sampleCount / channels
            if (frames <= 0) return
            if (sourceRate == VocalSeparationCache.SAMPLE_RATE && channels == 2) {
                for (i in 0 until frames) {
                    sink.push(pcm[i * 2] / 32768f, pcm[i * 2 + 1] / 32768f)
                }
                return
            }
            if (sourceRate == VocalSeparationCache.SAMPLE_RATE && channels == 1) {
                for (frame in 0 until frames) {
                    val value = pcm[frame] / 32768f
                    sink.push(value, value)
                }
                return
            }

            val chunkStart = inputFrames
            val chunkEnd = chunkStart + frames
            fun left(local: Int): Float =
                if (local < 0) previousLeft else pcm[local * channels] / 32768f
            fun right(local: Int): Float =
                if (local < 0) previousRight
                else if (channels == 1) pcm[local] / 32768f
                else pcm[local * channels + 1] / 32768f

            while (nextSourcePosition < chunkEnd - 1) {
                val global = nextSourcePosition
                val i0 = kotlin.math.floor(global).toLong()
                val fraction = (global - i0).toFloat()
                val local0 = (i0 - chunkStart).toInt()
                val l0 = left(local0)
                val l1 = left(local0 + 1)
                val r0 = right(local0)
                val r1 = right(local0 + 1)
                sink.push(l0 + (l1 - l0) * fraction, r0 + (r1 - r0) * fraction)
                nextSourcePosition += step
            }
            previousLeft = left(frames - 1)
            previousRight = right(frames - 1)
            inputFrames = chunkEnd
        }

        fun finish(sink: FloatStereoWriter) {
            // 末尾不足一个输出采样点时不补零，避免在歌曲尾部引入突变。
        }
    }

    private class FloatStereoWriter(file: File) : AutoCloseable {
        private val stream = BufferedOutputStream(FileOutputStream(file), 65_536)
        private val bytes = ByteArray(65_536)
        private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        private var position = 0
        var totalFrames: Int = 0
            private set

        fun push(left: Float, right: Float) {
            if (position + 8 > bytes.size) flush()
            buffer.putFloat(position, left)
            buffer.putFloat(position + 4, right)
            position += 8
            totalFrames++
        }

        private fun flush() {
            if (position > 0) {
                stream.write(bytes, 0, position)
                position = 0
            }
        }

        override fun close() {
            flush()
            stream.flush()
            stream.close()
        }
    }

    private class Pcm16Writer(file: File) : AutoCloseable {
        private val stream = BufferedOutputStream(FileOutputStream(file), 65_536)
        private val bytes = ByteArray(65_536)
        private var position = 0

        fun write(left: Short, right: Short) {
            if (position + 4 > bytes.size) flush()
            val l = left.toInt()
            val r = right.toInt()
            bytes[position] = (l and 0xFF).toByte()
            bytes[position + 1] = ((l ushr 8) and 0xFF).toByte()
            bytes[position + 2] = (r and 0xFF).toByte()
            bytes[position + 3] = ((r ushr 8) and 0xFF).toByte()
            position += 4
        }

        private fun flush() {
            if (position > 0) {
                stream.write(bytes, 0, position)
                position = 0
            }
        }

        override fun close() {
            flush()
            stream.flush()
            stream.close()
        }
    }

    private fun floatToPcm16(sample: Float): Short {
        val clamped = if (kotlin.math.abs(sample) > 1.0f) {
            kotlin.math.tanh(sample.toDouble()).toFloat()
        } else sample
        return (clamped * 32767f).toInt().coerceIn(-32768, 32767).toShort()
    }
}
