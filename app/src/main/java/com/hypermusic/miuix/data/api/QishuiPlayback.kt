package com.hypermusic.miuix.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 汽水车机接口只使用明文媒体；试听片段与完整歌曲明确区分。 */
internal class QishuiPlayback(private val client: QishuiClient) {
    enum class Quality { STANDARD, HIGH, LOSSLESS, HIRES }
    data class Audio(val url: String, val quality: Quality, val preview: Boolean, val clipStartMs: Long, val expiresAt: Long?, val qualityKnown: Boolean) {
        fun qualityLabel(): String = if (!qualityKnown) "未知音质" else when (quality) {
            Quality.HIRES -> "Hi-Res 无损"
            Quality.LOSSLESS -> "无损"
            Quality.HIGH -> "高品质"
            Quality.STANDARD -> "标准品质"
        }
    }
    private data class Candidate(val url: String, val quality: Quality, val bitrate: Long, val qualityKnown: Boolean)

    suspend fun resolve(track: JsonObject, requested: Quality): Audio = withContext(Dispatchers.IO) {
        resolve(track, requested, client::readPlayerInfo)
    }

    companion object {
        private val hosts = setOf("365yg.com", "douyinvod.com", "douyinscdn.com", "bytefcdn.com", "bytecdn.cn", "byteicdn.com", "ibytedtos.com", "pstatp.com", "snssdk.com", "bytedance.com", "bytetos.com", "volcvideo.com", "volccdn.com", "volces.com", "volcengineapi.com", "douyin.com", "qishui.com", "zijieapi.com", "zjcdn.com")

        internal fun trustedUrl(raw: String): String {
            val url = raw.toHttpUrlOrNull() ?: error("汽水音乐媒体地址无效")
            require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
                hosts.any { url.host == it || url.host.endsWith(".$it") }) { "汽水音乐媒体地址不可信" }
            return url.toString()
        }

        internal fun resolve(track: JsonObject, requested: Quality, fetch: (String) -> JsonObject): Audio {
            val player = track.obj("player_info") ?: error("汽水音乐未返回播放信息")
            for (variant in listOf("full", "preview")) {
                val part = player.obj(variant) ?: continue
                val info = part.obj("video_model_info") ?: continue
                val candidates = mutableListOf<Candidate>()
                var refused = false
                fun add(raw: String, quality: String, bitrate: Long) {
                    val url = runCatching { trustedUrl(raw) }.getOrNull()
                    if (url == null) refused = true else candidates += Candidate(url, qualityOf(quality), bitrate,
                        quality.lowercase() in setOf("medium", "higher", "highest", "lossless", "hi_res"))
                }
                val modelValue = info.get("video_model")
                if (modelValue != null && !modelValue.isJsonNull) {
                    val model = if (modelValue.isJsonObject) modelValue.asJsonObject else JsonParser.parseString(modelValue.asString).asJsonObject
                    model.getAsJsonArray("video_list")?.forEach { element ->
                        val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                        if (!encrypted(item)) {
                            val meta = item.obj("video_meta")
                            listOf(item.text("main_url"), item.text("backup_url"))
                                .filter { it.isNotBlank() }.distinct().forEach { address ->
                                    add(address, meta?.text("quality").orEmpty(), meta?.text("bitrate")?.toLongOrNull() ?: 0)
                                }
                        }
                    }
                }
                if (info.text("url_player_info").isNotBlank() &&
                    candidates.none { it.qualityKnown && it.quality >= requested }) {
                    val url = runCatching { trustedUrl(info.text("url_player_info")) }.getOrNull()
                    if (url == null) refused = true else {
                        val result = try { fetch(url) }
                            catch (error: java.util.concurrent.CancellationException) { throw error }
                            catch (error: Exception) { if (candidates.isEmpty()) throw error else null }
                        val list = result?.obj("Result")?.obj("Data")?.getAsJsonArray("PlayInfoList")
                            ?: result?.obj("data")?.getAsJsonArray("PlayInfoList") ?: result?.getAsJsonArray("PlayInfoList")
                        check(list != null || candidates.isNotEmpty()) { "汽水音乐播放列表缺失" }
                        list?.forEach { element ->
                            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                            if (!encrypted(item)) {
                                listOf(item.text("MainPlayUrl"), item.text("BackupPlayUrl"))
                                    .filter { it.isNotBlank() }.distinct().forEach { address ->
                                        add(address, item.text("Quality"), item.text("Bitrate").toLongOrNull() ?: 0)
                                    }
                            }
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    check(!refused) { "汽水音乐媒体地址不可信" }
                    continue
                }
                val allowed = candidates.filter { it.quality <= requested }
                val chosen = if (allowed.isNotEmpty()) allowed.maxWith(compareBy<Candidate> { it.quality }.thenBy { it.bitrate })
                    else candidates.minWith(compareBy<Candidate> { it.quality }.thenBy { it.bitrate })
                val expiry = info.text("expire_at").toLongOrNull()?.takeIf { it > System.currentTimeMillis() / 1000 }?.times(1000)
                val start = if (variant == "preview") part.obj("duration")?.text("start_ms")?.toLongOrNull()?.coerceAtLeast(0) ?: 0 else 0
                return Audio(chosen.url, chosen.quality, variant == "preview", start, expiry, chosen.qualityKnown)
            }
            error("汽水音乐未提供可播放的明文音频")
        }

        private fun qualityOf(value: String) = when (value.lowercase()) {
            "hi_res" -> Quality.HIRES; "lossless" -> Quality.LOSSLESS
            "higher", "highest" -> Quality.HIGH; else -> Quality.STANDARD
        }
        private fun encrypted(value: JsonObject): Boolean {
            val encryption = value.obj("encrypt_info")
            return listOf(encryption?.get("encrypt"), encryption?.get("spade_a"), value.get("PlayAuth"), value.get("Encrypt"), value.get("EncryptionMethod"))
                .any { field ->
                    field != null && !field.isJsonNull &&
                        (!field.isJsonPrimitive || field.asString.trim().lowercase() !in setOf("", "false", "0"))
                }
        }
        private fun JsonObject.obj(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
        private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }
}
