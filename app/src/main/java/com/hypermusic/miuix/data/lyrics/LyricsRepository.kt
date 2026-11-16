package com.hypermusic.miuix.data.lyrics

import android.util.Log
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.model.SongLyrics
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.ui.theme.ThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 多平台歌词来源：
 * - 各来源并发请求，先发布当前歌曲的原平台歌词，再确定最终结果
 * - 智能选择：Apple Music → QQ → 酷狗 → 网易云，平台偏好移至队首
 * - 启用罗马音时，以实际返回的罗马音优先；两边都有时优先歌曲原平台，其他音源优先 QQ
 * - 自动升级：按来源优先级选择逐字歌词；均无逐字时保留最高优先级普通歌词
 * - 手动匹配优先于自动选择，且使用用户指定歌曲的标识
 *
 * 各平台原始格式（YRC/QRC/KRC/LRC）统一交给 LyricParser，
 * 以复用无用歌词过滤、歌手行识别等逻辑。
 */
object LyricsRepository {

    private const val TAG = "LyricsRepository"
    internal const val INITIAL_WAIT_MS = 1_300L

    internal enum class Platform { NETEASE, QQ, KUGOU, APPLE, BODIAN, QISHUI }

    /** 歌词加载结果，[source] 为实际歌词来源名称。 */
    data class LyricsLoadResult(
        val lyrics: SongLyrics,
        val source: String?,
        val upgraded: Boolean,
        val rawDebugText: String = "",
        val matchedTrack: Track? = null,
        val rawDocument: RawLyricDocument? = null
    )

    internal data class LoadOutcome(
        val result: PlatformLyricResult,
        val upgraded: Boolean
    )

    suspend fun load(track: Track, preferDownload: Boolean = true, onUpdate: suspend (LyricsLoadResult) -> Unit = {}): Result<LyricsLoadResult> = withContext(Dispatchers.IO) {
        try {
            if (preferDownload) com.hypermusic.miuix.data.download.OfflineLibrary.loadLyrics(track)?.let {
                onUpdate(it)
                return@withContext Result.success(it)
            }
            supervisorScope {
                val saved = LyricMatches.find(track)
                val savedPlatform = when (saved?.platform) {
                    "netease" -> Platform.NETEASE
                    "qq" -> Platform.QQ
                    "kugou" -> Platform.KUGOU
                    "apple" -> Platform.APPLE
                    else -> null
                }
                val native = nativePlatform(track.source)
                val manual = saved != null && savedPlatform != null
                val order = if (manual) emptyList() else platformOrder(ThemeSettings.lyricSourcePreferenceIndex, ThemeSettings.showJapaneseRomaji, track.source)
                // Native lookup and automatic selection share the same request per platform.
                val requests = (order + listOfNotNull(native)).distinct().associateWith { platform ->
                    async { fetchSafely { fetch(platform, track) } }
                }
                val publishing = Mutex()
                var finalPublished = false
                val nativePublisher = native?.let { platform -> launch {
                    publishNativeFallback(requests.getValue(platform)) { result ->
                        publishing.withLock { if (!finalPublished) onUpdate(result.asLoadResult(false)) }
                    }
                } }
                try {
                    if (manual) {
                        val durationValid = LyricsMatch.durationMatches(track.duration, saved.match.duration)
                        val matched = if (durationValid) fetchSafely { fetch(savedPlatform, saved.match, exactMatch = true) } else null
                        if (matched != null) {
                            val result = matched.asLoadResult(false).copy(matchedTrack = saved.match)
                            com.hypermusic.miuix.data.download.OfflineLibrary.saveLyrics(track, result)
                            publishing.withLock { finalPublished = true; onUpdate(result) }
                            Result.success(result)
                        } else {
                            // Keep this song's native lyrics visible even when its explicit match fails.
                            nativePublisher?.join()
                            Result.failure(Exception(if (durationValid) "已匹配的歌词暂时不可用，请重试或重新匹配" else "已匹配歌词与歌曲时长相差超过6秒，请重新匹配"))
                        }
                    } else {
                        val outcome = selectLyrics(
                            order = order,
                            upgrade = ThemeSettings.wordByWordLyrics && ThemeSettings.autoUpgradeLyrics,
                            romanization = ThemeSettings.showJapaneseRomaji,
                            source = track.source,
                            onWinner = { winner -> publishing.withLock {
                                finalPublished = true
                                com.hypermusic.miuix.data.download.OfflineLibrary.saveLyrics(track, winner.result.asLoadResult(winner.upgraded))
                                onUpdate(winner.result.asLoadResult(winner.upgraded))
                            } }
                        ) { requests.getValue(it).await() }
                        outcome?.let { Result.success(it.result.asLoadResult(it.upgraded)) }
                            ?: Result.failure(Exception("未找到歌词"))
                    }
                } finally {
                    nativePublisher?.cancel()
                    requests.values.forEach { it.cancel() }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "load failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun PlatformLyricResult.asLoadResult(upgraded: Boolean) =
        LyricsLoadResult(lyrics, platformLabel(platform), upgraded, rawDebugText, matchedTrack, rawDocument)

    internal suspend fun publishNativeFallback(request: Deferred<PlatformLyricResult?>,
        publish: suspend (PlatformLyricResult) -> Unit) {
        // Start the grace period with loading, not after the native network response arrives.
        delay(INITIAL_WAIT_MS)
        request.await()?.let { publish(it) }
    }

    private suspend fun fetchSafely(block: suspend () -> PlatformLyricResult?): PlatformLyricResult? = try {
        withTimeoutOrNull(20_000L) { LyricRequests.scoped(block) }?.takeIf { it.lyrics.lines.isNotEmpty() || it.lyrics.isPureMusic }
    } catch (error: CancellationException) { throw error }
        catch (error: Exception) { Log.w(TAG, "歌词获取失败", error); null }

    private fun platformLabel(platform: String): String = when (platform) {
        "qq" -> "QQ音乐"
        "kugou" -> "酷狗音乐"
        "apple" -> "Apple Music"
        "bodian" -> "波点音乐"
        "qishui" -> "汽水音乐"
        else -> "网易云音乐"
    }

    internal suspend fun selectLyrics(order: List<Platform>, upgrade: Boolean, romanization: Boolean = false,
        source: String = "",
        onWinner: suspend (LoadOutcome) -> Unit = {},
        fetcher: suspend (Platform) -> PlatformLyricResult?): LoadOutcome? = supervisorScope {
        val platforms = order.distinct()
        if (platforms.isEmpty()) return@supervisorScope null
        data class Completed(val platform: Platform, val result: PlatformLyricResult?, val cancelled: CancellationException? = null)
        val completions = Channel<Completed>(Channel.UNLIMITED)
        val tasks = platforms.map { platform -> async {
            try {
                val result = try { fetcher(platform) } catch (error: CancellationException) { throw error }
                    catch (error: Exception) { Log.w(TAG, "歌词来源 $platform 获取失败", error); null }
                completions.send(Completed(platform, result?.takeIf { it.lyrics.lines.isNotEmpty() || it.lyrics.isPureMusic }))
            } catch (error: CancellationException) {
                completions.trySend(Completed(platform, null, error))
                throw error
            }
        } }
        val finished = mutableSetOf<Platform>()
        val results = mutableMapOf<Platform, PlatformLyricResult>()
        // Only the tie between actual QQ/NetEase romanized lyrics follows the song's source.
        // A missing native romanization still allows the other provider to win.
        val preferredRomanPlatform = if (source == "netease") Platform.NETEASE else Platform.QQ
        fun romanRank(platform: Platform): Int? = if (!romanization) null else when (platform) {
            Platform.QQ, Platform.NETEASE -> if (platform == preferredRomanPlatform) 0 else 1
            else -> null
        }
        fun rank(platform: Platform, result: PlatformLyricResult): Int {
            // Preserve a valid instrumental result, but do not let it hide real lyrics elsewhere.
            if (result.lyrics.isPureMusic) return 2 + platforms.size * 2 + platforms.indexOf(platform)
            val roman = romanRank(platform)
            if (roman != null && result.lyrics.lines.any { it.phonetic.isNotBlank() }) return roman
            return 2 + platforms.indexOf(platform) + if (upgrade && !result.hasWordLines) platforms.size else 0
        }
        fun potentialRank(platform: Platform) = romanRank(platform) ?: (2 + platforms.indexOf(platform))
        try {
            repeat(platforms.size) {
                val completed = completions.receive()
                completed.cancelled?.let { throw it }
                finished += completed.platform
                completed.result?.let { results[completed.platform] = it }
                val best = results.entries.minByOrNull { rank(it.key, it.value) }
                if (best != null && platforms.none { it !in finished && potentialRank(it) < rank(best.key, best.value) }) {
                    val first = platforms.firstNotNullOfOrNull { results[it] }
                    val winner = LoadOutcome(best.value, upgrade && best.value.hasWordLines && first?.hasWordLines == false)
                    onWinner(winner)
                    return@supervisorScope winner
                }
            }
            null
        } finally { tasks.forEach { it.cancel() }; completions.close() }
    }

    internal fun platformOrder(preference: Int, romanization: Boolean, source: String = "netease"): List<Platform> {
        val base = listOf(Platform.APPLE, Platform.QQ, Platform.KUGOU, Platform.NETEASE)
        val preferred = when (preference) {
            1 -> Platform.NETEASE
            2 -> Platform.QQ
            3 -> Platform.KUGOU
            4 -> Platform.APPLE
            else -> null
        }
        val native = nativePlatform(source)
        return (listOfNotNull(preferred) + base + listOfNotNull(native)).distinct()
    }

    private fun nativePlatform(source: String): Platform? = when (source) {
        "netease" -> Platform.NETEASE; "qqmusic" -> Platform.QQ; "kugou" -> Platform.KUGOU
        "bodian" -> Platform.BODIAN; "qishui" -> Platform.QISHUI; else -> null
    }

    suspend fun searchMatches(platform: String, track: Track): List<Track> {
        val keyword = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        return when (platform) {
            "netease" -> NeteaseApi.searchSongs(keyword).getOrThrow()
            "qq" -> com.hypermusic.miuix.data.api.QqMusicApi.searchSongs(keyword).getOrThrow()
            "kugou" -> KugouLyricsApi.searchTracks(track)
            "apple" -> AmllLyricsApi.search(track)
            else -> emptyList()
        }.filter { LyricsMatch.durationMatches(track.duration, it.duration) }
    }

    /** Inspect the selected candidate itself, independently of the playback romanization switch. */
    internal suspend fun matchFeatures(platform: String, track: Track): LyricFeatures? {
        val provider = when (platform) {
            "netease" -> Platform.NETEASE; "qq" -> Platform.QQ
            "kugou" -> Platform.KUGOU; "apple" -> Platform.APPLE
            else -> return null
        }
        return withTimeoutOrNull(20_000L) {
            LyricRequests.scoped { fetch(provider, track, exactMatch = true, showRomanization = true) }
        }?.let { LyricFeatures.from(it.lyrics, platform) }
    }

    private suspend fun fetch(platform: Platform, track: Track, exactMatch: Boolean = false, showRomanization: Boolean = ThemeSettings.showJapaneseRomaji): PlatformLyricResult? {
        return when (platform) {
            Platform.NETEASE -> {
                val neteaseTrack = if (track.source != "netease") {
                    val keyword = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
                    val candidates = NeteaseApi.searchSongs(keyword).getOrNull().orEmpty().map {
                        LyricsCandidate(name = it.title, artist = it.artist, album = it.album, duration = it.duration, extra = it)
                    }
                    LyricsMatch.pickBestCandidate(candidates, track)?.extra as? Track ?: return null
                } else track
                NeteaseApi.getSongLyric(
                    songId = neteaseTrack.id,
                    songTitle = track.title,
                    songArtist = track.artist,
                    songAlbum = track.album,
                    songDuration = track.duration,
                    artistNames = track.artistNames,
                    showRomanization = showRomanization
                ).getOrNull()?.let { (lyrics, raw) ->
                    if (lyrics.lines.isEmpty() && !lyrics.isPureMusic) return@let null
                    PlatformLyricResult(
                        lyrics = lyrics,
                        hasWordLines = lyrics.lines.any { !it.words.isNullOrEmpty() },
                        platform = "netease",
                        rawDocument = RawLyricDocument("lrc", raw.lrc, raw.tlyric, raw.yrc, raw.romalrc, raw.ytlrc, raw.yromalrc),
                        rawDebugText = formatRawLyricSections("YRC 逐字" to raw.yrc, "LRC" to raw.lrc,
                            "翻译 YTLRC" to raw.ytlrc, "翻译 TLYRIC" to raw.tlyric,
                            "罗马音 YROMALRC" to raw.yromalrc, "罗马音 ROMALRC" to raw.romalrc),
                        matchedTrack = neteaseTrack
                    )
                }
            }
            Platform.QQ -> QqMusicLyricsApi.fetch(track, showRomanization)
            Platform.KUGOU -> KugouLyricsApi.fetch(track)
            Platform.APPLE -> AmllLyricsApi.fetch(track, exactApple = exactMatch, showRomanization = showRomanization)
            Platform.BODIAN -> BodianLyricsApi.fetch(track)
            Platform.QISHUI -> QishuiLyricsApi.fetch(track)
        }
    }
}
