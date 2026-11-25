package com.hypermusic.miuix.data.api

import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class KugouPlayback(private val client: KugouClient, private val auth: KugouAuth) {
    enum class Quality(val parameter: String) { STANDARD("128"), HIGH("320"), LOSSLESS("flac"), HIRES("high") }
    data class Audio(val url: String, val requestedCandidate: Quality, val actualBitrate: Int?, val preview: Boolean, val extension: String? = null) {
        fun bitrateBps(): Int = actualBitrate?.let { if (it < 10_000) it * 1000 else it } ?: 0
        fun qualityLabel(): String = bitrateBps().takeIf { it > 0 }?.let { "${it / 1000}kbps" } ?: "未知音质"
    }

    suspend fun resolve(track: Track, quality: Quality): Audio = withContext(Dispatchers.IO) {
        require(track.source == "kugou") { "酷狗音乐歌曲标识缺失" }
        val hash = client.trackHash(track)
        val account = auth.account(playback = true)
        val album = track.albumId.takeIf { it.matches(Regex("[0-9]{1,20}")) }.orEmpty()
        val audioId = track.platform.kugouAlbumAudioId.takeIf { it.matches(Regex("[0-9]{1,20}")) }.orEmpty()
        selectAudio(quality, { candidate ->
            val variants = if (album.isNotEmpty() || audioId.isNotEmpty()) listOf(album to audioId, "" to "") else listOf("" to "")
            var preview: Audio? = null
            var failure: Exception? = null
            for ((albumId, albumAudioId) in variants) {
                val response = try { client.audioRequest(hash, albumId, albumAudioId, candidate.parameter, account) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { if (failure == null) failure = e; continue }
                val audio = parse(response, candidate, track.duration) ?: continue
                if (!audio.preview) return@selectAudio audio
                if (preview == null) preview = audio
            }
            preview ?: failure?.let { throw it }
        }, { parseLegacy(client.legacyAudioRequest(hash), track.duration) })
    }

    companion object {
        internal suspend fun selectAudio(quality: Quality, fetch: suspend (Quality) -> Audio?, legacy: suspend () -> Audio?): Audio {
            var preview: Audio? = null
            var failure: Exception? = null
            for (candidate in Quality.entries.take(quality.ordinal + 1).asReversed()) {
                val audio = try { fetch(candidate) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { if (failure == null) failure = e; continue }
                if (audio == null) continue
                if (!audio.preview) return audio
                if (preview == null) preview = audio
            }
            val fallback = try { legacy() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { if (failure == null) failure = e; null }
            if (fallback != null && !fallback.preview) return fallback
            return preview ?: fallback ?: throw (failure ?: IllegalStateException("酷狗音乐未返回可播放地址"))
        }
        internal fun parse(response: JsonObject, quality: Quality, originalDurationMs: Long): Audio? {
            val raw = response.get("data") ?: return null
            val data = when { raw.isJsonObject -> raw.asJsonObject; raw.isJsonArray -> raw.asJsonArray.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject; else -> null } ?: return null
            val value = listOf("play_url", "playUrl", "url").firstNotNullOfOrNull { key ->
                val field = data.get(key)
                when { field == null || field.isJsonNull -> null; field.isJsonArray -> field.asJsonArray.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString
                    field.isJsonPrimitive -> field.asString; else -> null }?.takeIf { it.isNotBlank() }
            } ?: return null
            val url = value.toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() } ?: return null
            val bitrate = data.text("bitrate").toIntOrNull()?.takeIf { it > 0 }
            val duration = data.text("timelength").toLongOrNull() ?: 0L
            val preview = listOf(data.text("is_free_part"), data.text("isFreePart")).any { it == "1" || it.equals("true", ignoreCase = true) } ||
                (originalDurationMs > 0 && duration > 0 && duration + 10_000L < originalDurationMs)
            val extension = data.text("extName").ifBlank { data.text("extname") }.lowercase().takeIf { it in setOf("mp3", "flac", "m4a", "aac", "ogg", "wav") }
            return Audio(url.toString(), quality, bitrate, preview, extension)
        }
        internal fun parseLegacy(data: JsonObject, originalDurationMs: Long): Audio? {
            val raw = data.text("url").ifBlank { data.text("backup_url") }
            val url = raw.toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() } ?: return null
            val duration = data.text("timeLength").toLongOrNull()?.times(1000) ?: 0L
            val preview = originalDurationMs > 0 && duration > 0 && duration + 10_000 < originalDurationMs
            val bitrate = data.text("bitRate").toIntOrNull()?.takeIf { it > 0 }
            val extension = data.text("extName").lowercase().takeIf { it in setOf("mp3", "flac", "m4a", "aac", "ogg", "wav") }
            return Audio(url.toString(), Quality.STANDARD, bitrate, preview, extension)
        }
        private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }
}
