package com.hypermusic.miuix.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.hypermusic.miuix.R
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.data.lyrics.LyricsRepository
import com.hypermusic.miuix.data.lyrics.formatEmbeddedLyrics
import com.hypermusic.miuix.data.model.AudioQuality
import com.hypermusic.miuix.ui.theme.ThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import kotlin.coroutines.coroutineContext

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val client = OkHttpClient()
    private class NetworkConfirmationNeeded : Exception()
    companion object {
        private val acquireLock = Mutex()
        private var activeCount = 0
    }

    override suspend fun doWork(): Result {
        var acquired = false
        try {
            while (!acquired) {
                acquireLock.withLock {
                    val limit = applicationContext.getSharedPreferences("downloads", Context.MODE_PRIVATE).getInt("parallel", 2).coerceIn(1, 5)
                    if (activeCount < limit) { activeCount++; acquired = true }
                }
                if (!acquired) delay(250)
            }
            return runDownload()
        } finally {
            if (acquired) acquireLock.withLock { activeCount-- }
        }
    }

    private suspend fun runDownload(): Result = withContext(Dispatchers.IO) {
        Downloads.init(applicationContext)
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val entry = Downloads.entries.find { it.id == id } ?: return@withContext Result.failure()
        val track = entry.track
        if (entry.waitForWifi && !Downloads.isWifi()) {
            Downloads.update(id) { it.copy(state = "queued", message = "等待 Wi-Fi") }
            return@withContext Result.retry()
        }
        if (!entry.allowMobile && !entry.waitForWifi && Downloads.shouldWarnOnCurrentNetwork()) {
            Downloads.requireNetworkDecision(id)
            return@withContext Result.success()
        }
        val folder = applicationContext.filesDir.resolve("downloads").apply { mkdirs() }
        val part = folder.resolve("$id.part")
        var savedFile: File? = null
        try {
            setForeground(foreground(track.title))
            if (QqMusicApi.accounts.isEmpty()) QqMusicApi.init(applicationContext)
            if (NeteaseApi.getAccounts().isEmpty()) NeteaseApi.init(applicationContext)
            ThemeSettings.init(applicationContext)
            com.hypermusic.miuix.data.lyrics.LyricMatches.init(applicationContext)
            com.hypermusic.miuix.data.lyrics.BodianLyricsApi.init(applicationContext)
            com.hypermusic.miuix.data.lyrics.KugouLyricsApi.init(applicationContext)
            com.hypermusic.miuix.data.lyrics.QishuiLyricsApi.init(applicationContext)
            com.hypermusic.miuix.data.lyrics.LyricExclusionRules.init(applicationContext)
            val (url, extension, quality) = if (track.source == "bodian") {
                val client = com.hypermusic.miuix.data.api.BodianClient(applicationContext, playback = true)
                val auth = com.hypermusic.miuix.data.api.BodianAuth(applicationContext, client)
                val revision = auth.sessionRevision()
                check(auth.currentAccount(playback = true)?.uid == entry.accountId) { "下载账号已移除或已切换，请重新下载" }
                val requested = when (entry.qualityIndex) {
                    0 -> com.hypermusic.miuix.data.api.BodianPlayback.Quality.LOSSLESS
                    1 -> com.hypermusic.miuix.data.api.BodianPlayback.Quality.HIGH
                    else -> com.hypermusic.miuix.data.api.BodianPlayback.Quality.STANDARD
                }
                val audio = com.hypermusic.miuix.data.api.BodianPlayback(client).resolve(track, requested)
                check(audio.preview == null) { "该歌曲只提供试听片段，不能下载完整歌曲" }
                check(auth.sessionRevision() == revision && auth.currentAccount(playback = true)?.uid == entry.accountId) { "下载账号已切换，请重新下载" }
                Triple(audio.url, requested.format, "未知音质")
            } else if (track.source == "qishui") {
                val api = com.hypermusic.miuix.data.api.QishuiClient(applicationContext, playback = true)
                val auth = com.hypermusic.miuix.data.api.QishuiAuth(applicationContext, api)
                val revision = auth.sessionGeneration()
                check(auth.account(playback = true)?.userId == entry.accountId) { "下载账号已切换，请重新下载" }
                val requested = when (entry.qualityIndex) {
                    0 -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.HIRES
                    1 -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.LOSSLESS
                    2 -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.HIGH
                    else -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.STANDARD
                }
                val detail = com.hypermusic.miuix.data.api.QishuiCatalog(api).songDetail(track.id.removePrefix("qishui_"))
                val audio = com.hypermusic.miuix.data.api.QishuiPlayback(api).resolve(detail, requested)
                check(!audio.preview) { "该歌曲只提供试听片段，不能下载完整歌曲" }
                check(auth.sessionGeneration() == revision && auth.account(playback = true)?.userId == entry.accountId) { "下载账号已切换，请重新下载" }
                Triple(audio.url, "bin", audio.qualityLabel())
            } else if (track.source == "kugou") {
                val api = com.hypermusic.miuix.data.api.KugouClient(applicationContext)
                val auth = com.hypermusic.miuix.data.api.KugouAuth(applicationContext, api)
                val revision = auth.sessionGeneration()
                check(auth.account(playback = true)?.userId == entry.accountId) { "下载账号已切换，请重新下载" }
                val requested = when (entry.qualityIndex) {
                    0 -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.HIRES
                    1 -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.LOSSLESS
                    2 -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.HIGH
                    else -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.STANDARD
                }
                val audio = com.hypermusic.miuix.data.api.KugouPlayback(api, auth).resolve(track, requested)
                check(!audio.preview) { "该歌曲只提供试听片段，不能下载完整歌曲" }
                check(auth.sessionGeneration() == revision && auth.account(playback = true)?.userId == entry.accountId) { "下载账号已切换，请重新下载" }
                val suffix = audio.extension ?: runCatching { java.net.URI(audio.url).path.substringAfterLast('.', "").lowercase() }.getOrDefault("")
                    .takeIf { it in setOf("mp3", "flac", "m4a", "aac", "ogg", "wav") } ?: "bin"
                Triple(audio.url, suffix, audio.qualityLabel())
            } else if (track.source == "qqmusic") {
                if (entry.accountId != null) check(QqMusicApi.accounts.any { it.credential.musicId == entry.accountId }) { "下载账号已移除，请重新下载" }
                val audio = QqMusicApi.getSongAudio(track.id, track.qqMediaMid, entry.qualityIndex, entry.accountId).getOrThrow()
                Triple(audio.url, audio.extension, audio.quality)
            } else if (track.source == "netease") {
                val cookie = entry.accountId?.let { NeteaseApi.getAccountCookie(it) ?: error("下载账号已移除，请重新下载") }
                val (url, bitrate, type) = NeteaseApi.getSongUrl(track.id, AudioQuality.neteaseBitrate(entry.qualityIndex), accountCookie = cookie).getOrThrow()
                Triple(url, type.ifBlank { "mp3" }, AudioQuality.neteaseActual(bitrate, type))
            } else error("暂不支持该音乐平台的下载")
            require(url.isNotBlank()) { "未获得歌曲地址" }
            val request = Request.Builder().url(url).apply {
                when (track.source) {
                    "qqmusic" -> header("Referer", "https://y.qq.com/")
                    "netease" -> header("Referer", "https://music.163.com/")
                }
            }.build()
            Downloads.update(id) { it.copy(state = "downloading", quality = quality, message = "") }
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "下载失败 (HTTP ${response.code})" }
                val body = response.body ?: error("服务器返回空文件")
                val contentType = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
                check(!contentType.startsWith("text/") && contentType != "application/json" && !contentType.endsWith("+json")) { "服务器没有返回音频文件" }
                val total = body.contentLength()
                var count = 0L
                var lastUpdate = 0L
                body.byteStream().use { input -> part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read); count += read
                        if (System.currentTimeMillis() - lastUpdate >= 500) {
                            if (entry.waitForWifi && !Downloads.isWifi()) throw java.io.IOException("Wi-Fi 已断开")
                            if (!entry.allowMobile && !entry.waitForWifi && Downloads.shouldWarnOnCurrentNetwork()) throw NetworkConfirmationNeeded()
                            Downloads.update(id) { it.copy(bytes = count, total = total) }
                            setForeground(foreground(track.title, count, total))
                            lastUpdate = System.currentTimeMillis()
                        }
                    }
                } }
                check(count > 0 && (total < 0 || count == total)) { "歌曲文件下载不完整" }
                Downloads.update(id) { it.copy(bytes = count, total = total, state = "tagging") }
            }
            coroutineContext.ensureActive()
            check(Downloads.entries.any { it.id == id }) { "任务已删除" }
            val requestedExt = extension.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "bin" }
            val header = part.inputStream().use { input -> ByteArray(512).let { bytes -> bytes.copyOf(input.read(bytes).coerceAtLeast(0)) } }
            check(!AudioFileFormat.looksLikeErrorDocument(header)) { "服务器没有返回音频文件" }
            val ext = AudioFileFormat.detect(header) ?: requestedExt
            val finalFile = folder.resolve("$id.$ext")
            savedFile = finalFile
            check(part.renameTo(finalFile)) { "无法保存歌曲" }
            val warnings = mutableListOf<String>()
            val lyric = LyricsRepository.load(track).getOrNull()?.lyrics?.let(::formatEmbeddedLyrics).orEmpty()
            if (lyric.isBlank()) warnings += "歌词不可用"
            val cover = if (track.imgUrl.isNotBlank()) runCatching {
                client.newCall(Request.Builder().url(track.imgUrl).build()).execute().use { response ->
                    check(response.isSuccessful); response.body?.bytes()?.takeIf { it.isNotEmpty() }
                }
            }.getOrNull() else null
            if (cover == null) warnings += "封面不可用"
            val staging = folder.resolve("$id-tag.$ext")
            try {
                finalFile.copyTo(staging, overwrite = true)
                org.jaudiotagger.tag.TagOptionSingleton.getInstance().isAndroid = true
                val audio = AudioFileIO.read(staging)
                if (track.source == "bodian") {
                    val rate = audio.audioHeader.bitRateAsNumber.coerceIn(0L, Int.MAX_VALUE.toLong() / 1000).toInt() * 1000
                    val actual = com.hypermusic.miuix.data.model.bodianActualQuality(ext == "flac", rate)
                    Downloads.update(id) { it.copy(quality = actual) }
                }
                val tag = audio.tagOrCreateAndSetDefault
                tag.setField(FieldKey.TITLE, track.title)
                tag.setField(FieldKey.ARTIST, track.artist)
                tag.setField(FieldKey.ALBUM, track.album)
                if (lyric.isNotBlank()) {
                    try { tag.setField(FieldKey.LYRICS, lyric) }
                    catch (e: Exception) { warnings += "歌词未写入" }
                }
                if (cover != null) try {
                    if (tag is org.jaudiotagger.tag.flac.FlacTag) {
                        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(cover, 0, cover.size, options)
                        check(options.outWidth > 0 && options.outHeight > 0) { "封面图片无效" }
                        val mime = options.outMimeType ?: "image/jpeg"
                        tag.deleteArtworkField()
                        tag.setField(tag.createArtworkField(cover, 3, mime, "", options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0), 24, 0))
                    } else {
                        val image = folder.resolve("$id-cover.jpg")
                        try { image.writeBytes(cover); tag.setField(ArtworkFactory.createArtworkFromFile(image)) } finally { image.delete() }
                    }
                } catch (e: Exception) {
                    warnings += "封面未写入"
                    android.util.Log.e("DownloadWorker", "写入封面失败", e)
                }
                audio.commit()
                check(staging.renameTo(finalFile)) { "无法写入标签" }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("DownloadWorker", "写入音频标签失败", e)
                warnings += "封面或歌词未写入：${e.message ?: "格式不支持"}"
            } finally { staging.delete() }
            coroutineContext.ensureActive()
            if (!Downloads.finish(id, finalFile.absolutePath, finalFile.length(), warnings.joinToString("；"))) {
                finalFile.delete()
                return@withContext Result.failure()
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete(); savedFile?.delete(); throw e
        } catch (e: NetworkConfirmationNeeded) {
            part.delete(); savedFile?.delete()
            Downloads.requireNetworkDecision(id)
            Result.success()
        } catch (e: java.io.IOException) {
            part.delete(); savedFile?.delete()
            Downloads.update(id) { it.copy(state = "queued", bytes = 0, total = -1, message = "网络中断，等待重试") }
            Result.retry()
        } catch (e: Exception) {
            part.delete(); savedFile?.delete()
            Downloads.update(id) { it.copy(state = "failed", message = e.message ?: "下载失败") }
            Result.failure()
        }
    }

    private fun foreground(title: String, current: Long = 0, total: Long = -1): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("download_tasks", "歌曲下载", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "download_tasks")
            .setSmallIcon(R.drawable.ic_music_note).setContentTitle("正在下载歌曲").setContentText(title)
            .setOngoing(true).setProgress(100, if (total > 0) ((current.toDouble() / total * 100).toInt()).coerceIn(0, 100) else 0, total <= 0).build()
        return ForegroundInfo(id.hashCode(), notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}
