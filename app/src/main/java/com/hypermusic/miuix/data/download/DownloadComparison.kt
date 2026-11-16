package com.hypermusic.miuix.data.download

import android.content.Context
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.model.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class DownloadComparison(val quality: String, val size: Long?, val error: String? = null)

fun DownloadEntry.qualityLabel(): String = quality.ifBlank {
    if (state == "complete") return@ifBlank "未知音质"
    val platform = MusicPlatform.fromTrackSource(track.source) ?: MusicPlatform.NETEASE
    AudioQuality.options(platform).getOrNull(qualityIndex)?.let { "$it（最高设置）" } ?: "未知音质"
}
fun DownloadEntry.comparisonSize(): Long? = (if (state == "complete")
    path.takeIf { it.isNotBlank() }?.let { java.io.File(it).takeIf(java.io.File::isFile)?.length() } ?: bytes else total).takeIf { it > 0 }

/** Compare provider bytes with provider bytes, excluding locally embedded artwork and lyrics. */
fun DownloadEntry.sourceAudioSize(): Long? = total.takeIf { it > 0 }

suspend fun inspectDownload(context: Context, entry: DownloadEntry): DownloadComparison = withContext(Dispatchers.IO) {
    val (url, _, quality) = DownloadAudioResolver.resolve(context, entry)
    val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    fun request(range: Boolean): Request = Request.Builder().url(url).apply {
        if (range) header("Range", "bytes=0-0") else head()
        when (entry.track.source) {
            "qqmusic" -> header("Referer", "https://y.qq.com/")
            "netease" -> header("Referer", "https://music.163.com/")
        }
    }.build()
    fun length(range: Boolean): Long? = runCatching {
        client.newCall(request(range)).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val type = response.header("Content-Type").orEmpty()
            if (type.startsWith("text/") || type.contains("json")) return@use null
            if (range && response.code == 206) response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.takeIf { it > 0 }
            else response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
        }
    }.getOrNull()
    DownloadComparison(quality, length(false) ?: length(true))
}
