package com.hypermusic.miuix.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.SongLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** NetEase lyric fetching, source fallback and raw-field extraction. */
internal class NeteaseLyricsApi(
    private val client: OkHttpClient,
    private val gson: Gson,
    private val cookies: () -> String,
    private val ensureCookie: () -> Unit
) {
    suspend fun getSongLyric(
        songId: String,
        songTitle: String,
        songArtist: String,
        songAlbum: String,
        songDuration: Long,
        artistNames: List<String>,
        showRomanization: Boolean
    ): Result<Pair<SongLyrics, NeteaseApi.RawLyrics>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_")
            val json = fetchLyricJson(id)
                ?: return@withContext Result.failure(Exception("Empty response"))

            val lrc = json.lyricField("lrc")
            val yrc = json.lyricField("yrc")
            val tlyric = json.lyricField("tlyric")
            val ytlrc = json.lyricField("ytlrc")
            val romalrc = json.lyricField("romalrc")
            val yromalrc = json.lyricField("yromalrc")

            // 逐字歌词优先使用配套的 ytlrc（时间戳与 yrc 行边界对齐）
            val translation = if (ytlrc.isNotEmpty()) ytlrc else tlyric
            // 罗马音优先使用逐字配套的 yromalrc；可在设置中关闭
            val phonetic = if (showRomanization) {
                if (yromalrc.isNotEmpty()) yromalrc else romalrc
            } else {
                ""
            }

            android.util.Log.d(
                "NeteaseApi",
                "getSongLyric - lrc=${lrc.length}, yrc=${yrc.length}, tlyric=${tlyric.length}, " +
                    "ytlrc=${ytlrc.length}, romalrc=${romalrc.length}, yromalrc=${yromalrc.length}"
            )

            // 统一交给 LyricParser 处理（各平台通用）
            val result = com.hypermusic.miuix.data.lyrics.LyricParser.parse(
                lrc = lrc,
                tlyric = translation,
                yrc = yrc,
                songTitle = songTitle,
                songArtist = songArtist,
                songAlbum = songAlbum,
                artistNames = artistNames,
                phonetic = phonetic
            )

            android.util.Log.d(
                "NeteaseApi",
                "getSongLyric - parsed ${result.lines.size} lines, " +
                    "wordLines=${result.lines.count { !it.words.isNullOrEmpty() }}, isPureMusic=${result.isPureMusic}"
            )
            Result.success(result to NeteaseApi.RawLyrics(lrc, yrc, tlyric, ytlrc, romalrc, yromalrc))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getSongLyric error: ${e.message}")
            Result.failure(e)
        }
    }

    /** 获取网易云原始歌词（不经过任何处理）。 */
    suspend fun getRawLyric(songId: String): Result<NeteaseApi.RawLyrics> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_")
            val json = fetchLyricJson(id)
                ?: return@withContext Result.failure(Exception("Empty response"))
            Result.success(
                NeteaseApi.RawLyrics(
                    lrc = json.lyricField("lrc"),
                    yrc = json.lyricField("yrc"),
                    tlyric = json.lyricField("tlyric"),
                    ytlrc = json.lyricField("ytlrc"),
                    romalrc = json.lyricField("romalrc"),
                    yromalrc = json.lyricField("yromalrc")
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getRawLyric error: ${e.message}")
            Result.failure(e)
        }
    }

    /** 读取 { "lrc": { "lyric": "..." } } 形式的歌词字段。 */
    private fun JsonObject.lyricField(name: String): String {
        if (!has(name) || get(name).isJsonNull) return ""
        val obj = getAsJsonObject(name)
        if (!obj.has("lyric") || obj.get("lyric").isJsonNull) return ""
        return obj.get("lyric").asString
    }

    /**
     * 获取歌词 JSON（优先新版接口，含逐字歌词）：
     * 1) /api/song/lyric/v1（eapi）：yrc 逐字歌词、ytlrc 逐字翻译
     * 2) 失败时回退 /weapi/song/lyric
     */
    private fun fetchLyricJson(id: String): JsonObject? {
        // 1) 客户端 API（与 SPlayer-Next 的 lyric_new 一致）
        try {
            val form = FormBody.Builder()
                .add("id", id)
                .add("cp", "false")
                .add("tv", "0")
                .add("lv", "0")
                .add("rv", "1")
                .add("kv", "0")
                .add("yv", "0")
                .add("ytv", "0")
                .add("yrv", "1")
                .build()
            val request = Request.Builder()
                .url("https://music.163.com/api/song/lyric/v1")
                .post(form)
                .header("Referer", "https://music.163.com")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.29.205117"
                )
                .header("Cookie", "os=pc; ${cookies()}")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
            executeLyricRequest(request, "api v1")?.let { return it }
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "fetchLyricJson - api v1 error: ${e.message}")
        }

        // 2) eapi（部分设备/网络环境走 eapi 更稳定）
        try {
            val eapiPath = "/api/song/lyric/v1"
            val reqData = """{"id":$id,"cp":false,"tv":0,"lv":0,"rv":1,"kv":0,"yv":0,"ytv":0,"yrv":1}"""
            val encrypted = NeteaseCrypto.eapi(eapiPath, reqData)
            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .build()
            val request = Request.Builder()
                .url("https://interface3.music.163.com/eapi/song/lyric/v1")
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", "os=pc; ${cookies()}")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
            executeLyricRequest(request, "eapi v1")?.let { return it }
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "fetchLyricJson - eapi v1 error: ${e.message}")
        }

        // 3) 回退：旧版 weapi 接口
        return try {
            val reqData = """{"id":$id,"lv":-1,"tv":-1,"rv":-1,"yv":-1,"ytv":-1,"yrv":-1,"csrf_token":""}"""
            val encrypted = NeteaseCrypto.weapi(reqData)
            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()
            val request = Request.Builder()
                .url("https://music.163.com/weapi/song/lyric?csrf_token=")
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
            executeLyricRequest(request, "weapi", requireLyric = false)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "fetchLyricJson - weapi error: ${e.message}")
            null
        }
    }

    /** 执行歌词请求；[requireLyric] 为 true 时要求响应中至少含 lrc 或 yrc。 */
    private fun executeLyricRequest(
        request: Request,
        source: String,
        requireLyric: Boolean = true
    ): JsonObject? {
        val response = client.newCall(request).execute()
        val responseBody = if (response.isSuccessful) response.body?.string() else null
        if (responseBody.isNullOrEmpty()) {
            android.util.Log.d("NeteaseApi", "fetchLyricJson - $source empty, code=${response.code}")
            return null
        }
        val json = runCatching { gson.fromJson(responseBody, JsonObject::class.java) }.getOrNull()
        if (json == null) {
            android.util.Log.d("NeteaseApi", "fetchLyricJson - $source invalid JSON")
            return null
        }
        if (requireLyric &&
            json.lyricField("yrc").isEmpty() &&
            json.lyricField("lrc").isEmpty()
        ) {
            android.util.Log.d("NeteaseApi", "fetchLyricJson - $source no lyric content")
            return null
        }
        android.util.Log.d("NeteaseApi", "fetchLyricJson - $source success, length=${responseBody.length}")
        return json
    }
}
