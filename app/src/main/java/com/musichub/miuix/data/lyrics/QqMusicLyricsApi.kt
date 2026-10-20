package com.musichub.miuix.data.lyrics

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.musichub.miuix.data.model.SongLyrics
import com.musichub.miuix.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** 单个平台解析后的歌词结果。 */
data class PlatformLyricResult(
    val lyrics: SongLyrics,
    val hasWordLines: Boolean,
    val platform: String
)

/**
 * QQ 音乐歌词：搜索匹配 → GetPlayLyricInfo 获取 QRC（失败时回退 LRC 接口）。
 * 参考 SPlayer-Next 的 qqmusic 歌词实现。
 */
object QqMusicLyricsApi {

    private const val TAG = "QqMusicLyricsApi"
    private const val API_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
    private const val LRC_URL = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private data class SongRef(val id: String, val mid: String)

    private val refCache = ConcurrentHashMap<String, SongRef>()

    suspend fun fetch(track: Track): PlatformLyricResult? = withContext(Dispatchers.IO) {
        try {
            val ref = refCache[track.id]
                ?: search(track)?.also { refCache[track.id] = it }
                ?: return@withContext null
            fetchLyric(track, ref) ?: fetchLrc(track, ref.mid)
        } catch (e: Exception) {
            Log.e(TAG, "fetch failed: ${e.message}")
            null
        }
    }

    private fun search(track: Track): SongRef? {
        val keyword = listOf(track.title, track.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (keyword.isBlank()) return null

        val param = JsonObject().apply {
            addProperty("searchid", System.nanoTime().toString())
            addProperty("query", keyword)
            addProperty("page_num", 1)
            addProperty("num_per_page", 20)
            addProperty("search_type", 0)
            addProperty("highlight", true)
            addProperty("grp", 1)
            add("selectors", JsonObject())
            add("vec_selectors", JsonArray())
        }
        val root = requestFcg("music.search.SearchCgiService", "DoSearchForQQMusicMobile", param)
            ?: return null

        val songs = root.obj("request")?.obj("data")
            ?.obj("body")?.arr("item_song")
            ?: return null
        if (songs.size() == 0) return null

        val candidates = mutableListOf<LyricsCandidate<SongRef>>()
        for (element in songs) {
            if (!element.isJsonObject) continue
            val song = element.asJsonObject
            val id = song.str("id") ?: continue
            val mid = song.str("mid") ?: ""
            val name = stripHighlight(song.str("title") ?: "")
            val interval = song.get("interval")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
            val album = stripHighlight(song.obj("album")?.str("name") ?: "")
            val artist = song.arr("singer")
                ?.joinToString(" / ") { singer ->
                    if (singer.isJsonObject) stripHighlight(singer.asJsonObject.str("name") ?: "") else ""
                }
                ?: ""
            candidates.add(
                LyricsCandidate(
                    name = name,
                    artist = artist,
                    album = album,
                    duration = interval * 1000L,
                    extra = SongRef(id, mid)
                )
            )
        }

        val best = LyricsMatch.pickBestCandidate(candidates, track) ?: return null
        @Suppress("UNCHECKED_CAST")
        return (best.extra as? SongRef)?.takeIf { it.id.isNotEmpty() }
    }

    private fun fetchLyric(track: Track, ref: SongRef): PlatformLyricResult? {
        val param = JsonObject().apply {
            addProperty("albumName", b64(track.album))
            addProperty("crypt", 1)
            addProperty("ct", 19)
            addProperty("cv", 2111)
            addProperty("interval", (track.duration / 1000L).toInt())
            addProperty("lrc_t", 0)
            addProperty("qrc", 1)
            addProperty("qrc_t", 0)
            addProperty("roma", 1)
            addProperty("roma_t", 0)
            addProperty("singerName", b64(track.artist))
            addProperty("songID", ref.id.toLongOrNull() ?: 0L)
            addProperty("songName", b64(track.title))
            addProperty("trans", 1)
            addProperty("trans_t", 0)
            addProperty("type", 0)
        }
        val root = requestFcg("music.musichallSong.PlayLyricInfo", "GetPlayLyricInfo", param)
            ?: return null
        val data = root.obj("request")?.obj("data") ?: return null

        val lyricHex = data.str("lyric") ?: return null
        if (lyricHex.isBlank()) return null

        val qrcTimestamp = data.str("qrc_t")?.toLongOrNull() ?: 0L
        val encryptedText = runCatching { QqMusicQrcDecryptor.decrypt(lyricHex) }.getOrNull()
            ?: return null
        val translation = decryptMaybePlain(data.str("trans"))

        val lyrics = if (qrcTimestamp != 0L && encryptedText.trimStart().startsWith("<")) {
            LyricParser.parseQrc(
                qrc = encryptedText,
                tlyric = translation,
                songTitle = track.title,
                songArtist = track.artist,
                artistNames = track.artistNames
            )
        } else {
            LyricParser.parse(
                lrc = encryptedText,
                tlyric = translation,
                yrc = "",
                songTitle = track.title,
                songArtist = track.artist,
                artistNames = track.artistNames
            )
        }

        if (lyrics.lines.isEmpty()) return null
        return PlatformLyricResult(
            lyrics = lyrics,
            hasWordLines = lyrics.lines.any { !it.words.isNullOrEmpty() },
            platform = "qq"
        )
    }

    private fun fetchLrc(track: Track, mid: String): PlatformLyricResult? {
        if (mid.isBlank()) return null
        val url = "$LRC_URL?songmid=$mid&format=json&nobase64=1&g_tk=5381" +
            "&loginUin=0&hostUin=0&inCharset=utf8&outCharset=utf-8&notice=0" +
            "&platform=yqq.json&needNewCode=0"
        val root = getJson(url) ?: return null
        val lyric = root.str("lyric") ?: return null
        if (lyric.isBlank()) return null
        val translation = decodeMaybeBase64(root.str("trans") ?: "")

        val lyrics = LyricParser.parse(
            lrc = lyric,
            tlyric = translation,
            yrc = "",
            songTitle = track.title,
            songArtist = track.artist,
            artistNames = track.artistNames
        )
        if (lyrics.lines.isEmpty()) return null
        return PlatformLyricResult(
            lyrics = lyrics,
            hasWordLines = false,
            platform = "qq"
        )
    }

    private fun decryptMaybePlain(value: String?): String {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return ""
        if (text.startsWith("[")) return text
        runCatching { QqMusicQrcDecryptor.decrypt(text) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return decodeMaybeBase64(text)
    }

    private fun decodeMaybeBase64(value: String): String {
        val text = value.trim()
        if (text.isEmpty()) return ""
        if (text.startsWith("[")) return text
        return runCatching {
            val decoded = String(java.util.Base64.getMimeDecoder().decode(text), Charsets.UTF_8)
            if (decoded.contains("[")) decoded else text
        }.getOrDefault(text)
    }

    private fun stripHighlight(value: String): String = value.replace(Regex("</?em>"), "")

    private fun requestFcg(module: String, method: String, param: JsonObject): JsonObject? {
        val body = JsonObject().apply {
            add("comm", commonComm())
            add("request", JsonObject().apply {
                addProperty("module", module)
                addProperty("method", method)
                add("param", param)
            })
        }
        return postJson(API_URL, body, "https://y.qq.com")
    }

    private fun commonComm(): JsonObject = JsonObject().apply {
        addProperty("ct", 11)
        addProperty("cv", 14090008)
        addProperty("v", 14090008)
        addProperty("chid", "10003505")
        addProperty("os_ver", "15")
        addProperty("phonetype", "24122RKC7C")
        addProperty("tmeAppID", "qqmusic")
        addProperty("nettype", "NETWORK_WIFI")
        addProperty("udid", "0")
        addProperty("OpenUDID", "0")
        addProperty("QIMEI36", "0")
        addProperty("uin", "0")
    }

    private fun postJson(url: String, body: JsonObject, referer: String): JsonObject? {
        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(body).toRequestBody(jsonMediaType))
            .header("User-Agent", "QQMusic 14090008(android 15)")
            .header("Referer", referer)
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string() ?: return@use null
                gson.fromJson(text, JsonObject::class.java)
            }
        }.getOrNull()
    }

    private fun getJson(url: String): JsonObject? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://y.qq.com/portal/player.html")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string() ?: return@use null
                gson.fromJson(text, JsonObject::class.java)
            }
        }.getOrNull()
    }

    private fun b64(text: String): String =
        java.util.Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString
}
