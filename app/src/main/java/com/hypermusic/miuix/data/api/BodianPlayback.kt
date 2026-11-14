package com.hypermusic.miuix.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 保留试听范围，调用方不得将试听片段当成完整歌曲缓存或下载。 */
internal class BodianPlayback(private val client: BodianClient) {
    enum class Quality(val format: String, val bitrate: String) {
        STANDARD("mp3", "128kmp3"), HIGH("mp3", "320kmp3"), LOSSLESS("flac", "2000kflac")
    }
    // checkRight's audition range uses seconds (unlike search payInfo's millisecond fields).
    data class Preview(val startSeconds: Long, val endSeconds: Long)
    data class Audio(val url: String, val requestedQuality: Quality, val preview: Preview? = null)
    private val gson = Gson()

    suspend fun resolve(track: Track, quality: Quality): Audio = withContext(Dispatchers.IO) {
        require(track.source == "bodian")
        val rawId = track.id.removePrefix("bodian_")
        val id = rawId.toLongOrNull()?.takeIf { it > 0 && it <= 9_007_199_254_740_991L } ?: error("无效的波点音乐歌曲 ID")
        val freeSign = track.bodianFreeSign.orEmpty()
        require(freeSign.length <= 4096)
        val rightBody = linkedMapOf<String, Any>("musicId" to id, "freeSign" to freeSign)
        val right = client.call("/api/play/music/v2/checkRight", mapOf("musicId" to rawId, "freeSign" to freeSign),
            signed = true, body = gson.toJson(rightBody)).getAsJsonObject("data") ?: error("波点音乐未返回播放权限")
        val status = right.get("status")?.asInt ?: error("波点音乐未返回播放权限")
        if (status == 3) {
            val part = right.getAsJsonObject("audition") ?: error("歌曲试听信息缺失")
            return@withContext Audio(url(part), Quality.STANDARD, previewRange(part))
        }
        check(status != 7) { "当前账号没有播放这首歌曲的权限" }
        val body = linkedMapOf<String, Any>("devId" to client.deviceId, "musicId" to id, "format" to quality.format,
            "br" to quality.bitrate, "freeSign" to freeSign)
        val query = body.mapValues { it.value.toString() }
        val audio = client.call("/api/play/music/v2/audioUrl", query, signed = true, body = gson.toJson(body))
            .getAsJsonObject("data") ?: error("波点音乐未返回音频")
        Audio(url(audio), quality)
    }

    private fun url(data: JsonObject): String {
        fun text(name: String) = data.get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val value = text("audioHttpsUrl").ifBlank { text("audioUrl") }.ifBlank { text("https") }.ifBlank { text("url") }
        val parsed = value.toHttpUrlOrNull() ?: error("波点音乐返回了无效音频地址")
        check(parsed.username.isEmpty() && parsed.password.isEmpty()) { "波点音乐返回了无效音频地址" }
        return parsed.toString()
    }

    companion object {
        internal fun previewRange(part: JsonObject): Preview {
            val start = part.get("start")?.asLong ?: error("试听起点缺失")
            val end = part.get("end")?.asLong ?: error("试听终点缺失")
            check(start >= 0 && end > start) { "试听范围无效" }
            return Preview(start, end)
        }
    }
}
