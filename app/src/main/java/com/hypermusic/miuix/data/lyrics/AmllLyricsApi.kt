package com.hypermusic.miuix.data.lyrics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume

/** AMLL TTML DB is community-maintained; its entries are not guaranteed for every song. */
object AmllLyricsApi {
    private const val API_BASE = "https://api.amll.dev/v1/lyrics"
    private const val MIRROR = "https://amlldb.bikonoo.com"
    private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()

    internal suspend fun request(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "HyperMusic/2.0").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("歌词服务请求失败（HTTP ${it.code}）")
                        it.body?.string()?.takeIf(String::isNotBlank) ?: throw IOException("歌词服务返回空数据")
                    }
                }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
    private suspend fun body(url: String): String? = try { request(url) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { null }
    private fun JsonObject.string(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    private fun JsonObject.strings(key: String): List<String> = getAsJsonArray(key)?.mapNotNull {
        runCatching { it.asString }.getOrNull()?.takeIf(String::isNotBlank)
    }.orEmpty()

    /** Search lyric entries rather than Apple's catalogue, whose songs frequently have no TTML. */
    suspend fun search(track: Track): List<Track> = withContext(Dispatchers.IO) {
        val title = track.title.trim()
        if (title.isBlank()) return@withContext emptyList()
        val url = "$API_BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("musicName", title).addQueryParameter("pageSize", "50").build().toString()
        val root = try { JsonParser.parseString(request(url)).asJsonObject }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { throw IOException("AMLL 词库搜索失败：${e.message}", e) }
        kotlin.coroutines.coroutineContext.ensureActive()
        val items = root.getAsJsonObject("data")?.getAsJsonArray("items")
            ?: throw IOException("AMLL 词库返回的数据格式不正确")
        val records = items.mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject }
        val appleIds = records.flatMap { it.strings("appleMusicIds") }.distinct().take(100)
        val metadata = lookupAppleMetadata(appleIds)
        kotlin.coroutines.coroutineContext.ensureActive()
        records.mapNotNull { item ->
            val id = item.string("id").takeIf { it.isNotBlank() && it.all(Char::isDigit) } ?: return@mapNotNull null
            val filename = item.string("filename").takeIf { it.matches(Regex("[A-Za-z0-9._-]+\\.ttml")) }.orEmpty()
            val titleName = item.strings("musicNames").firstOrNull().orEmpty()
            val artists = item.strings("artistNames")
            val albumName = item.strings("albumNames").firstOrNull().orEmpty()
            val apple = item.strings("appleMusicIds").firstNotNullOfOrNull { metadata[it] }
            val sameSong = LyricsMatch.normalize(titleName) == LyricsMatch.normalize(track.title) &&
                artists.any { name -> track.artistNames.ifEmpty { listOf(track.artist) }.any { LyricsMatch.normalize(it) == LyricsMatch.normalize(name) } }
            Track(id = "amll_$id", title = titleName, artist = artists.joinToString(" / "), artistId = "",
                album = albumName, albumId = "", source = "apple",
                sourceUrl = if (filename.isNotEmpty()) "$MIRROR/raw-lyrics/$filename" else "",
                imgUrl = apple?.imgUrl?.takeIf(String::isNotBlank) ?: track.imgUrl.takeIf { sameSong }.orEmpty(),
                duration = apple?.duration?.takeIf { it > 0 } ?: track.duration.takeIf { sameSong } ?: 0L)
        }.distinctBy { it.id }
    }

    private suspend fun lookupAppleMetadata(ids: List<String>): Map<String, Track> {
        if (ids.isEmpty()) return emptyMap()
        val url = "https://itunes.apple.com/lookup".toHttpUrl().newBuilder()
            .addQueryParameter("id", ids.joinToString(",")).addQueryParameter("entity", "song")
            .addQueryParameter("country", "US").build().toString()
        val results = body(url)?.let { runCatching { JsonParser.parseString(it).asJsonObject.getAsJsonArray("results") }.getOrNull() }
            ?: return emptyMap()
        return results.mapNotNull { entry -> runCatching {
            val o = entry.asJsonObject
            val id = o.string("trackId").takeIf(String::isNotBlank) ?: return@runCatching null
            id to Track(id = id, title = o.string("trackName"), artist = o.string("artistName"), artistId = "",
                album = o.string("collectionName"), albumId = "", source = "apple", sourceUrl = "",
                imgUrl = o.string("artworkUrl100").replace("100x100", "600x600"),
                duration = o.string("trackTimeMillis").toLongOrNull() ?: 0L)
        }.getOrNull() }.toMap()
    }

    private suspend fun apiLyrics(parameter: String, id: String): String? {
        if (id.isBlank()) return null
        val url = "$API_BASE/get".toHttpUrl().newBuilder().addQueryParameter(parameter, id).build().toString()
        return body(url)?.let { raw -> runCatching {
            JsonParser.parseString(raw).asJsonObject.getAsJsonObject("data")?.string("lyrics")
        }.getOrNull() }?.takeIf(String::isNotBlank)
    }

    suspend fun fetch(track: Track, exactApple: Boolean = false, showRomanization: Boolean = com.hypermusic.miuix.ui.theme.ThemeSettings.showJapaneseRomaji): PlatformLyricResult? = withContext(Dispatchers.IO) {
        val jobContext = kotlin.coroutines.coroutineContext
        fun parse(xml: String?): PlatformLyricResult? {
            jobContext.ensureActive()
            if (xml.isNullOrBlank()) return null
            val lyrics = runCatching { TtmlLyricsParser.parse(xml, track, showRomanization) }.getOrNull() ?: return null
            if (lyrics.lines.isEmpty() && !lyrics.isPureMusic) return null
            return PlatformLyricResult(lyrics, lyrics.lines.any { !it.words.isNullOrEmpty() }, "apple",
                rawDebugText = formatRawLyricSections("AMLL TTML 原歌词" to xml), rawDocument = RawLyricDocument("ttml", xml))
        }
        suspend fun mirror(path: String, id: String): String? {
            if (id.isBlank() || !id.all(Char::isLetterOrDigit)) return null
            return body("$MIRROR/$path/$id.ttml")
        }
        when {
            track.source == "apple" && track.id.startsWith("amll_") -> {
                val id = track.id.removePrefix("amll_")
                if (!id.all(Char::isDigit)) return@withContext null
                parse(apiLyrics("id", id))?.let { return@withContext it }
                // Only accept the mirror URL created by search; do not fetch arbitrary saved URLs.
                val filename = track.sourceUrl.removePrefix("$MIRROR/raw-lyrics/")
                if (filename.matches(Regex("[A-Za-z0-9._-]+\\.ttml"))) {
                    parse(body("$MIRROR/raw-lyrics/$filename"))?.let { return@withContext it }
                }
                return@withContext null
            }
            track.source == "apple" -> {
                val id = track.id
                if (!id.all(Char::isDigit)) return@withContext null
                parse(apiLyrics("appleMusicId", id))?.let { return@withContext it }
                parse(mirror("am-lyrics", id))?.let { return@withContext it }
                // Old matches were saved from Apple's catalogue, which may not have a DB ID.
                // Only rematch a uniquely identified title, artist and (when known) album.
                val candidates = try { search(track) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { emptyList() }
                val exactCandidates = candidates.filter { candidate ->
                    LyricsMatch.durationMatches(track.duration, candidate.duration) &&
                        LyricsMatch.normalize(candidate.title) == LyricsMatch.normalize(track.title) &&
                        candidate.artist.split(" / ").any { LyricsMatch.normalize(it) == LyricsMatch.normalize(track.artist) } &&
                        (candidate.album.isBlank() || track.album.isBlank() || LyricsMatch.normalize(candidate.album) == LyricsMatch.normalize(track.album))
                }
                if (exactCandidates.size == 1) {
                    val candidate = exactCandidates.single()
                    parse(apiLyrics("id", candidate.id.removePrefix("amll_")))?.let { return@withContext it }
                }
                return@withContext null
            }
            track.source == "qqmusic" -> {
                val ids = listOf(track.id.removePrefix("qqtrack_"), track.platform.qqSongId.takeIf { it > 0 }?.toString().orEmpty())
                for (id in ids.filter(String::isNotBlank).distinct()) {
                    parse(mirror("qq-lyrics", id))?.let { return@withContext it }
                    parse(apiLyrics("qqMusicId", id))?.let { return@withContext it }
                }
            }
            track.source == "netease" -> {
                val id = track.id.removePrefix("netrack_")
                parse(mirror("ncm-lyrics", id))?.let { return@withContext it }
                parse(apiLyrics("ncmMusicId", id))?.let { return@withContext it }
            }
            else -> Unit // Other providers must match by song metadata, not by a NetEase ID.
        }
        if (!exactApple) {
            val matches = try { search(track) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { emptyList() }
            val candidates = matches.map {
                LyricsCandidate(name = it.title, artist = it.artist, album = it.album, duration = it.duration, extra = it)
            }
            val best = LyricsMatch.pickBestCandidate(candidates, track)?.extra as? Track
            if (best != null) parse(apiLyrics("id", best.id.removePrefix("amll_")))?.let { return@withContext it }
        }
        null
    }
}
