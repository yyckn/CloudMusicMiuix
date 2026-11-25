package com.hypermusic.miuix.data.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** Listening reports and account-scoped listening history for NetEase. */
internal class NeteaseListeningApi(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val contextProvider: () -> Context?,
    private val accountCookie: (String) -> String?
) {
    private suspend fun listeningRequest(path: String, data: JsonObject, cookie: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            check(cookie.isNotBlank()) { "请先登录网易云音乐" }
            val weblog = path == "/api/feedback/weblog"
            val header = JsonObject().apply {
                cookie.split(';').map { it.trim().split('=', limit = 2) }.filter { it.size == 2 && it[0] !in setOf("os", "appver") }.forEach { addProperty(it[0], it[1]) }
                addProperty("os", if (weblog) "osx" else "pc")
                addProperty("appver", if (weblog) "3.1.10.5100" else "3.1.17.204416")
                addProperty("osver", if (weblog) "15.5" else "Microsoft-Windows-10-Professional-build-19045-64bit")
                addProperty("versioncode", "140"); addProperty("channel", "netease")
                addProperty("resolution", "1920x1080")
                addProperty("requestId", "${System.currentTimeMillis()}_${(1000..9999).random()}")
                val prefs = contextProvider()?.getSharedPreferences("netease_prefs", Context.MODE_PRIVATE)
                val deviceId = synchronized(this@NeteaseListeningApi) {
                    prefs?.getString("qr_pc_device_id", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").also {
                        prefs?.edit()?.putString("qr_pc_device_id", it)?.apply()
                    }
                }
                addProperty("deviceId", deviceId)
            }
            data.addProperty("csrf_token", header.get("__csrf")?.asString.orEmpty())
            data.addProperty("e_r", false)
            data.add("header", header)
            val body = FormBody.Builder().add("params", NeteaseCrypto.eapi(path, data.toString()).getValue("params")).build()
            val domain = if (weblog) "https://clientlog.music.163.com" else "https://interfacepc.music.163.com"
            val request = Request.Builder().url("$domain${path.replaceFirst("/api/", "/eapi/")}")
                .header("Cookie", header.entrySet().joinToString("; ") { "${it.key}=${it.value.asString}" })
                .header("User-Agent", if (weblog) "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36"
                    else "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 NeteaseMusicDesktop/3.1.17.204416")
                .header("Referer", "https://music.163.com/").post(body).build()
            val received = suspendCancellableCoroutine<okhttp3.Response> { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, error: java.io.IOException) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        continuation.resume(response) { _, abandoned, _ -> abandoned.close() }
                    }
                })
            }
            received.use { response ->
                check(response.isSuccessful) { "网易云请求失败（HTTP ${response.code}）" }
                val result = gson.fromJson(response.body?.string(), JsonObject::class.java) ?: error("网易云返回空数据")
                val code = result.get("code")?.asInt ?: 200
                check(code == 200) { result.get("message")?.asString ?: "网易云请求失败（$code）" }
                result
            }
        }
    }

    /** Same direct endpoints used by AuraPlayer; all parallel sections use one pinned account. */
    suspend fun listeningCloudSection(section: String, type: String, endTime: Long?, accountId: String): Result<JsonObject> {
        val cookie = accountCookie(accountId)
            ?: return Result.failure(Exception("请先登录网易云音乐"))
        val data = JsonObject()
        val path = when (section) {
            "recent" -> { data.addProperty("limit", 300); "/api/play-record/song/list" }
            "today" -> "/api/content/activity/listen/data/today/song/play/rank"
            "realtime" -> { data.addProperty("type", type); "/api/content/activity/listen/data/realtime/report" }
            "report", "rank" -> {
                data.addProperty("type", type)
                endTime?.let { data.addProperty("endTime", it) }
                "/api/content/activity/listen/data/" + if (section == "rank") "song/play/rank" else "report"
            }
            "year" -> "/api/content/activity/listen/data/year/report"
            "total" -> "/api/content/activity/listen/data/total"
            else -> return Result.failure(IllegalArgumentException("未知听歌报告类型"))
        }
        return listeningRequest(path, data, cookie)
    }

    suspend fun reportListening(track: Track, accountId: String, listenedMs: Long, startedAt: Long): Result<Unit> {
        val cookie = accountCookie(accountId) ?: return Result.failure(Exception("原播放账号已移除，等待该账号重新登录"))
        val songId = track.id.removePrefix("netrack_").toLongOrNull()
            ?: return Result.failure(Exception("歌曲没有有效的网易云 ID"))
        val logs = JsonArray()
        listOf("startplay", "play").forEach { action ->
            logs.add(JsonObject().apply {
                addProperty("action", action)
                add("json", JsonObject().apply {
                    addProperty("id", songId); addProperty("type", "song")
                    addProperty("source", "list"); addProperty("sourceId", 0)
                    addProperty("mainsite", "1"); addProperty("mainsiteWeb", "1")
                    addProperty("content", "id=0"); addProperty("time", listenedMs / 1000)
                    addProperty("end", "playend"); addProperty("download", if (track.localDownloadId == null) 0 else 1)
                    addProperty("wifi", 0); addProperty("startTime", startedAt)
                })
            })
        }
        for (log in logs) {
            val result = listeningRequest("/api/feedback/weblog", JsonObject().apply {
                addProperty("logs", JsonArray().apply { add(log) }.toString())
            }, cookie)
            if (result.isFailure) return Result.failure(requireNotNull(result.exceptionOrNull()))
        }
        return Result.success(Unit)
    }
}
