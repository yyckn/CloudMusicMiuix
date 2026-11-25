package com.hypermusic.miuix.data.audio.separation

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface VocalModelDownloadState {
    data object Idle : VocalModelDownloadState
    data class Downloading(val downloaded: Long, val total: Long, val bytesPerSecond: Long) : VocalModelDownloadState
    data object Verifying : VocalModelDownloadState
    data object Ready : VocalModelDownloadState
    data class Failed(val message: String) : VocalModelDownloadState
}

/** 端侧模型目录、完整性校验与下载任务。 */
object VocalSeparationModelStore {
    private const val DIR = "vocal_separation_models"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val calls = mutableMapOf<String, Call>()
    private val mutableStates = MutableStateFlow<Map<String, VocalModelDownloadState>>(emptyMap())
    val states = mutableStates.asStateFlow()

    private var appContext: Context? = null
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun init(context: Context) {
        if (appContext != null) return
        synchronized(this) {
            if (appContext != null) return
            appContext = context.applicationContext
            mutableStates.value = VocalSeparationModel.entries.associate { model ->
                model.id to if (isReady(model)) VocalModelDownloadState.Ready else VocalModelDownloadState.Idle
            }
        }
    }

    fun directory(context: Context = requireContext()): File =
        File(context.filesDir, DIR).apply { if (!isDirectory) mkdirs() }

    fun file(model: VocalSeparationModel, context: Context = requireContext()): File =
        File(directory(context), model.fileName)

    fun marker(model: VocalSeparationModel, context: Context = requireContext()): File =
        File(directory(context), "${model.fileName}.verified")

    fun isReady(model: VocalSeparationModel, context: Context = requireContext()): Boolean {
        val file = file(model, context)
        if (!file.isFile || file.length() != model.sizeBytes) return false
        val marker = marker(model, context)
        return marker.isFile && marker.readText().trim().equals(model.sha256, ignoreCase = true)
    }

    fun downloadedBytes(model: VocalSeparationModel): Long =
        appContext?.let { file(model, it).takeIf(File::isFile)?.length() } ?: 0L

    fun state(model: VocalSeparationModel): VocalModelDownloadState =
        mutableStates.value[model.id] ?: VocalModelDownloadState.Idle

    @Synchronized
    fun refresh() {
        val context = appContext ?: return
        mutableStates.value = VocalSeparationModel.entries.associate { model ->
            model.id to when {
                model.id in calls -> state(model)
                isReady(model, context) -> VocalModelDownloadState.Ready
                else -> VocalModelDownloadState.Idle
            }
        }
    }

    @Synchronized
    fun download(model: VocalSeparationModel) {
        val context = appContext ?: return
        if (model.id in calls) return
        if (isReady(model, context)) {
            updateState(model, VocalModelDownloadState.Ready)
            return
        }
        val call = client.newCall(Request.Builder().url(model.url).build())
        calls[model.id] = call
        updateState(model, VocalModelDownloadState.Downloading(0L, model.sizeBytes, 0L))
        scope.launch {
            val target = file(model, context)
            val partial = File(target.path + ".part")
            try {
                partial.parentFile?.mkdirs()
                if (partial.exists()) partial.delete()
                call.execute().use { response ->
                    if (!response.isSuccessful) error("模型下载失败（HTTP ${response.code}）")
                    val body = response.body ?: error("模型下载响应为空")
                    val total = if (model.sizeBytes > 0) model.sizeBytes else body.contentLength()
                    var downloaded = 0L
                    var lastBytes = 0L
                    var lastTime = android.os.SystemClock.elapsedRealtime()
                    FileOutputStream(partial).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastTime >= 250L) {
                                    val speed = ((downloaded - lastBytes) * 1000L) / (now - lastTime).coerceAtLeast(1L)
                                    updateState(model, VocalModelDownloadState.Downloading(downloaded, total, speed))
                                    lastBytes = downloaded
                                    lastTime = now
                                }
                            }
                        }
                    }
                    updateState(model, VocalModelDownloadState.Downloading(downloaded, total, 0L))
                }
                updateState(model, VocalModelDownloadState.Verifying)
                val actual = sha256(partial)
                if (actual != model.sha256 || partial.length() != model.sizeBytes) {
                    partial.delete()
                    error("模型校验失败，请重试")
                }
                synchronized(this@VocalSeparationModelStore) {
                    if (!call.isCanceled()) {
                        if (target.exists()) target.delete()
                        check(partial.renameTo(target)) { "无法保存模型文件" }
                        marker(model, context).writeText(model.sha256)
                    }
                    partial.delete()
                    calls.remove(model.id)
                    updateState(
                        model,
                        if (call.isCanceled()) VocalModelDownloadState.Idle else VocalModelDownloadState.Ready,
                    )
                }
            } catch (error: Exception) {
                synchronized(this@VocalSeparationModelStore) {
                    partial.delete()
                    calls.remove(model.id)
                    updateState(
                        model,
                        if (call.isCanceled()) VocalModelDownloadState.Idle
                        else VocalModelDownloadState.Failed(error.message ?: "模型下载失败"),
                    )
                }
            }
        }
    }

    @Synchronized
    fun cancelDownload(model: VocalSeparationModel) {
        calls[model.id]?.cancel()
    }

    @Synchronized
    fun delete(model: VocalSeparationModel) {
        val context = appContext ?: return
        if (model.id in calls) return
        file(model, context).delete()
        marker(model, context).delete()
        File(file(model, context).path + ".part").delete()
        updateState(model, VocalModelDownloadState.Idle)
    }

    @Synchronized
    private fun updateState(model: VocalSeparationModel, state: VocalModelDownloadState) {
        mutableStates.value = mutableStates.value + (model.id to state)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun requireContext(): Context =
        appContext ?: error("VocalSeparationModelStore 尚未初始化")
}
