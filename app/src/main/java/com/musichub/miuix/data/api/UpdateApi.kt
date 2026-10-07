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
    private const val UPDATE_URL = "https://musichub.rth1.xyz/json/updatelog.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * 获取更新日志。5 秒超时（超时即失败，不重试）。
     * 支持两种 JSON 结构：
     * 1) 单个对象（可带 history）：{ "version": "1.1.0", "date": "...", "changelog": ["..."], "downloadUrl": "...", "history": [ {...} ] }
     * 2) 版本对象数组（按版本号自动排序）：[ { "version": "1.1.0", ... }, { "version": "1.0.0", ... } ]
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
                val element = JsonParser.parseString(body)

                val latest: UpdateInfo
                val history: List<UpdateInfo>
                if (element.isJsonArray) {
                    // 数组形式：按版本号从高到低排序，第一个为最新
                    val list = element.asJsonArray
                        .mapNotNull { if (it.isJsonObject) parseVersion(it.asJsonObject) else null }
                        .filter { it.version.isNotEmpty() }
                        .sortedWith(Comparator { a, b -> compareVersions(b.version, a.version) })
                    if (list.isEmpty()) {
                        return@withContext Result.failure(Exception("No version in array"))
                    }
                    latest = list.first()
                    history = list.drop(1)
                } else if (element.isJsonObject) {
                    val json = element.asJsonObject
                    if (!json.has("version") || json.get("version").isJsonNull) {
                        return@withContext Result.failure(Exception("No version field"))
                    }
                    latest = parseVersion(json)
                    val h = mutableListOf<UpdateInfo>()
                    if (json.has("history") && json.get("history").isJsonArray) {
                        json.getAsJsonArray("history").forEach { el ->
                            if (el.isJsonObject) h.add(parseVersion(el.asJsonObject))
                        }
                        h.sortWith(Comparator { a, b -> compareVersions(b.version, a.version) })
                    }
                    history = h
                } else {
                    return@withContext Result.failure(Exception("Unsupported JSON root"))
                }
                Result.success(latest.copy(history = history))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun versionParts(v: String): List<Int> {
        // 去掉 v 前缀与 -release/-beta1/+build 等后缀，并取每段前导数字（如 3-beta1 → 3）
        val main = v.trim().removePrefix("v").removePrefix("V")
            .substringBefore("-").substringBefore("+")
        return main.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = versionParts(a)
        val pb = versionParts(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    /** 解析单个版本对象：version / date / changelog / downloadUrl */
    private fun parseVersion(json: com.google.gson.JsonObject): UpdateInfo {
        val version = if (json.has("version") && !json.get("version").isJsonNull) json.get("version").asString else ""
        val date = if (json.has("date") && !json.get("date").isJsonNull) json.get("date").asString else ""
        val changelog = mutableListOf<String>()
        if (json.has("changelog") && !json.get("changelog").isJsonNull) {
            val el = json.get("changelog")
            if (el.isJsonArray) {
                el.asJsonArray.forEach { if (!it.isJsonNull) changelog.add(it.asString) }
            } else {
                changelog.addAll(el.asString.split("\n").map { it.trim() }.filter { it.isNotEmpty() })
            }
        }
        val downloadUrl = if (json.has("downloadUrl") && !json.get("downloadUrl").isJsonNull) {
            json.get("downloadUrl").asString
        } else {
            ""
        }
        return UpdateInfo(version = version, date = date, changelog = changelog, downloadUrl = downloadUrl)
    }
}
