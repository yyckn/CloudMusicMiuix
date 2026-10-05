package com.musichub.miuix.data.api

import com.google.gson.JsonParser
import com.musichub.miuix.data.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 应用更新检查 */
object UpdateApi {
    private const val UPDATE_URL = "https://musichub.rth1.xyz/updatelog.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * 获取更新日志。5 秒超时（超时即失败，不重试）。
     * JSON 结构示例见项目根目录 updatelog.json：
     * { "version": "1.1.0", "date": "2025-10-06", "changelog": ["...", "..."], "downloadUrl": "..." }
     */
    suspend fun fetchUpdateLog(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(UPDATE_URL)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Empty body"))
                }
                val json = JsonParser.parseString(body).asJsonObject
                if (!json.has("version") || json.get("version").isJsonNull) {
                    return@withContext Result.failure(Exception("No version field"))
                }
                val version = json.get("version").asString
                val date = if (json.has("date") && !json.get("date").isJsonNull) json.get("date").asString else ""
                val changelog = mutableListOf<String>()
                if (json.has("changelog") && !json.get("changelog").isJsonNull) {
                    val el = json.get("changelog")
                    if (el.isJsonArray) {
                        el.asJsonArray.forEach { if (!it.isJsonNull) changelog.add(it.asString) }
                    } else {
                        changelog.addAll(
                            el.asString.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    }
                }
                val downloadUrl = if (json.has("downloadUrl") && !json.get("downloadUrl").isJsonNull) {
                    json.get("downloadUrl").asString
                } else {
                    ""
                }
                Result.success(
                    UpdateInfo(
                        version = version,
                        date = date,
                        changelog = changelog,
                        downloadUrl = downloadUrl
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
