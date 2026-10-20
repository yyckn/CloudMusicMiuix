package com.musichub.miuix.data.lyrics

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.musichub.miuix.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.InflaterInputStream

/**
 * 酷狗音乐歌词：搜索匹配 → lyrics.kugou.com 获取 KRC/LRC。
 * 参考 SPlayer-Next 的 kugou 歌词实现。
 */
object KugouLyricsApi {

    private const val TAG = "KugouLyricsApi"
    private const val SEARCH_URL = "https://songsearch.kugou.com/song_search_v2"
    private const val LYRIC_SEARCH_URL = "https://lyrics.kugou.com/search"
    private const val LYRIC_DOWNLOAD_URL = "https://lyrics.kugou.com/download"

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val lyricHeaders = mapOf(
        "KG-RC" to "1",
        "KG-THash" to "expand_search_manager.cpp:852736169:451",
        "User-Agent" to "KuGou2012-9020-ExpandSearchManager"
    )

    private val hashCache = ConcurrentHashMap<String, String>()

    suspend fun fetch(track: Track): PlatformLyricResult? = withContext(Dispatchers.IO) {
        try {
            val hash = hashCache[track.id]
                ?: searchHash(track)?.also { hashCache[track.id] = it }
                ?: return@withContext null
            fetchLyric(track, hash)
        } catch (e: Exception) {
            Log.e(TAG, "fetch failed: ${e.message}")
            null
        }
    }

    private fun searchHash(track: Track): String? {
        val keyword = listOf(track.title, track.artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (keyword.isBlank()) return null

        val url = "$SEARCH_URL?keyword=${encode(keyword)}&page=1&pagesize=20" +
            "&userid=0&clientver=&platform=WebFilter&filter=2" +
            "&iscorrection=1&privilege_filter=0&area_code=1"
        val root = getJson(url, mapOf("User-Agent" to "Mozilla/5.0")) ?: return null
        val lists = root.obj("data")?.arr("lists") ?: return null

        val candidates = mutableListOf<LyricsCandidate<String>>()
        fun addSong(song: JsonObject) {
            val hash = song.str("FileHash") ?: return
            if (hash.isBlank()) return
            val name = decodeHtml(song.str("SongName") ?: "")
            val duration = song.get("Duration")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
            val album = decodeHtml(song.str("AlbumName") ?: "")
            val artist = song.arr("Singers")
                ?.joinToString(" / ") { singer ->
                    if (singer.isJsonObject) decodeHtml(singer.asJsonObject.str("name") ?: "") else ""
                }
                ?: decodeHtml(song.str("SingerName").orEmpty())
            candidates.add(
                LyricsCandidate(
                    name = name,
                    artist = artist,
                    album = album,
                    duration = duration * 1000L,
                    extra = hash
                )
            )
        }

        for (element in lists) {
            if (!element.isJsonObject) continue
            val song = element.asJsonObject
            addSong(song)
            song.arr("Grp")?.forEach { group ->
                if (group.isJsonObject) addSong(group.asJsonObject)
            }
        }

        val best = LyricsMatch.pickBestCandidate(candidates, track) ?: return null
        return best.extra as? String
    }

    private fun fetchLyric(track: Track, hash: String): PlatformLyricResult? {
        val seconds = (track.duration / 1000L).toInt()
        val searchUrl = "$LYRIC_SEARCH_URL?ver=1&man=yes&client=pc&lrctxt=1" +
            "&keyword=${encode(track.title)}&hash=$hash&timelength=$seconds"
        val searchRoot = getJson(searchUrl, lyricHeaders) ?: return null
        val candidates = searchRoot.arr("candidates") ?: return null
        if (candidates.size() == 0) return null

        // 优先带逐字信息的候选
        val candidate = candidates
            .mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
            .firstOrNull { c ->
                c.get("krctype")?.takeIf { !it.isJsonNull }?.asInt == 1 &&
                    c.get("contenttype")?.takeIf { !it.isJsonNull }?.asInt != 1
            }
            ?: candidates.first().asJsonObject

        val id = candidate.str("id") ?: return null
        val accessKey = candidate.str("accesskey") ?: return null
        val preferKrc = candidate.get("krctype")?.takeIf { !it.isJsonNull }?.asInt == 1 &&
            candidate.get("contenttype")?.takeIf { !it.isJsonNull }?.asInt != 1

        if (preferKrc) {
            download(track, id, accessKey, "krc")?.let { return it }
        }
        download(track, id, accessKey, "lrc")?.let { return it }
        return if (!preferKrc) download(track, id, accessKey, "krc") else null
    }

    private fun download(
        track: Track,
        id: String,
        accessKey: String,
        fmt: String
    ): PlatformLyricResult? {
        val url = "$LYRIC_DOWNLOAD_URL?ver=1&client=pc&charset=utf8" +
            "&id=${encode(id)}&accesskey=${encode(accessKey)}&fmt=$fmt"
        val root = getJson(url, lyricHeaders) ?: return null
        val content = root.str("content") ?: return null
        if (content.isBlank()) return null
        val actualFmt = root.str("fmt")?.lowercase() ?: fmt

        return if (actualFmt == "krc") {
            val raw = decodeKrc(content) ?: return null
            val translation = extractKrcTranslation(raw)
            val lyrics = LyricParser.parseKrc(
                krc = raw,
                tlyric = translation,
                songTitle = track.title,
                songArtist = track.artist,
                artistNames = track.artistNames
            )
            if (lyrics.lines.isEmpty()) return null
            PlatformLyricResult(
                lyrics = lyrics,
                hasWordLines = lyrics.lines.any { !it.words.isNullOrEmpty() },
                platform = "kugou"
            )
        } else {
            val lrc = runCatching {
                String(java.util.Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
            }.getOrNull() ?: return null
            val lyrics = LyricParser.parse(
                lrc = lrc,
                tlyric = "",
                yrc = "",
                songTitle = track.title,
                songArtist = track.artist,
                artistNames = track.artistNames
            )
            if (lyrics.lines.isEmpty()) return null
            PlatformLyricResult(
                lyrics = lyrics,
                hasWordLines = false,
                platform = "kugou"
            )
        }
    }

    /** KRC：base64 → 去掉前 4 字节 → 16 字节 key 循环 XOR → zlib 解压。 */
    private fun decodeKrc(content: String): String? = runCatching {
        val bytes = java.util.Base64.getMimeDecoder().decode(content)
        if (bytes.size <= 4) return@runCatching null
        val data = bytes.copyOfRange(4, bytes.size)
        val key = byteArrayOf(
            0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
            0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69
        )
        for (i in data.indices) {
            data[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        InflaterInputStream(ByteArrayInputStream(data)).bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }
    }.getOrNull()

    /** 从 KRC 的 [language:BASE64] 中提取翻译，并转换为按行时间戳的 LRC。 */
    private fun extractKrcTranslation(krc: String): String {
        val match = Regex("""\[language:([\w=\\/+]+)]""").find(krc) ?: return ""
        return runCatching {
            val json = String(java.util.Base64.getMimeDecoder().decode(match.groupValues[1]), Charsets.UTF_8)
            val root = gson.fromJson(json, JsonObject::class.java)
            val content = root.arr("content") ?: return@runCatching ""

            var transLines: List<String>? = null
            for (element in content) {
                if (!element.isJsonObject) continue
                val obj = element.asJsonObject
                val type = obj.get("type")?.takeIf { !it.isJsonNull }?.asInt ?: continue
                val lines = obj.arr("lyricContent")?.mapNotNull { line ->
                    if (!line.isJsonArray) return@mapNotNull null
                    line.asJsonArray.joinToString("") { part ->
                        if (part.isJsonNull) "" else part.asString
                    }
                } ?: continue
                if (type == 1) transLines = lines
            }
            val lines = transLines ?: return@runCatching ""

            val starts = Regex("""^\[(\d+),(\d+)]""", RegexOption.MULTILINE)
                .findAll(krc)
                .map { it.groupValues[1].toLong() }
                .toList()

            buildString {
                lines.forEachIndexed { index, text ->
                    val time = starts.getOrNull(index) ?: return@forEachIndexed
                    if (text.isBlank()) return@forEachIndexed
                    val minutes = time / 60_000
                    val seconds = (time % 60_000) / 1000
                    val millis = time % 1000
                    append("[%02d:%02d.%03d]%s\n".format(minutes, seconds, millis, text))
                }
            }
        }.getOrDefault("")
    }

    private fun getJson(url: String, headers: Map<String, String>): JsonObject? {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> builder.header(key, value) }
        return runCatching {
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string() ?: return@use null
                gson.fromJson(text, JsonObject::class.java)
            }
        }.getOrNull()
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun decodeHtml(value: String): String =
        value.replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#039;", "'")
            .replace("&amp;", "&")

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arr(name: String): JsonArray? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.str(name: String): String? =
        get(name)?.takeIf { !it.isJsonNull }?.asString
}
