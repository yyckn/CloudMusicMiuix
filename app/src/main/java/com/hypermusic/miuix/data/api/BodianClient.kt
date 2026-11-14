package com.hypermusic.miuix.data.api

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class BodianApiException(val code: Int?, message: String) : Exception(message)

/** 波点桌面接口传输层；账号及设备标识独立于其他平台。 */
internal class BodianClient(context: Context, private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(com.hypermusic.miuix.data.lyrics.LyricRequests)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build(), private val playback: Boolean = false) {
    private val appContext = context.applicationContext
    private val prefs = context.applicationContext.getSharedPreferences("bodian_prefs", Context.MODE_PRIVATE)
    internal val deviceId = synchronized(deviceLock) {
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            check(prefs.edit().putString("device_id", it).commit()) { "无法保存波点音乐设备标识" }
        }
    }

    fun call(path: String, parameters: Map<String, String> = emptyMap(), signed: Boolean = false, anonymous: Boolean = false,
             body: String? = null, method: String = "GET", lyric: Boolean = false): JsonObject {
        require(path.startsWith('/') && !path.contains(".."))
        require(method == "GET" || method == "POST")
        val selected = if (playback && !anonymous) AdditionalAccounts(appContext, MusicPlatform.BODIAN).playbackAccount() else null
        val uid = if (anonymous) "-1" else if (playback) selected?.id ?: "-1" else prefs.getString("uid", "-1").orEmpty()
        val token = if (anonymous) "" else if (playback) selected?.values?.get("token").orEmpty() else prefs.getString("token", "").orEmpty()
        val query = LinkedHashMap(parameters).apply { put("uid", uid); put("token", token) }
        if (signed) {
            query["timestamp"] = System.currentTimeMillis().toString()
            query["sign"] = sign(path, query, body.orEmpty())
        }
        val url = (if (lyric) "https://mlyric.kuwo.cn" else "https://bd-api.kuwo.cn").toHttpUrl().newBuilder()
            .encodedPath(path).apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val builder = Request.Builder().url(url).header("User-Agent", "Dart/3.3 (dart:io)")
            .header("plat", "win").header("channel", "W1").header("ver", "1.1.7")
            .header("svrver", "13").header("api-ver", "application/json").header("brand", "Windows")
            .header("net", "wifi").header("devid", deviceId).header("qimei36", deviceId)
            .header("Content-Type", "application/json")
        if (!anonymous && uid != "-1" && token.isNotEmpty()) builder.header("uid", uid).header("token", token)
        val requestBody = body?.toRequestBody("application/json".toMediaType())
        // The two playback endpoints accept the same signed JSON fields in the query.
        // OkHttp does not support GET request bodies, so send those fields in the query
        // while retaining the original JSON in the signature.
        if (method == "POST") builder.post(requestBody ?: ByteArray(0).toRequestBody(null)) else {
            require(body == null || path in setOf("/api/play/music/v2/checkRight", "/api/play/music/v2/audioUrl")) {
                "此接口不支持带请求体的 GET 请求"
            }
            builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "波点音乐请求失败（HTTP ${response.code}）" }
            val body = response.body ?: error("波点音乐响应为空")
            val source = body.source()
            check(body.contentLength() <= 8 * 1024 * 1024 && !source.request(8L * 1024 * 1024 + 1)) { "波点音乐响应过大" }
            val json = JsonParser.parseString(source.readUtf8()).asJsonObject
            val code = json.get("code")?.asInt
            if (code != 200) throw BodianApiException(code, "波点音乐请求失败（${code ?: "未知"}）")
            return json
        }
    }

    companion object {
        private val deviceLock = Any()
        internal fun sign(path: String, query: Map<String, String>, body: String): String {
            fun md5(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val encoded = query.entries.joinToString("&") { (k, v) ->
                "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }
            val chars = encoded.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.toCharArray().sorted().joinToString("")
            return md5("kuwotest$chars" + if (body.isNotEmpty()) md5(body + "kuwotest") + path else path)
        }
    }
}
