package com.musichub.miuix.data.lyrics

import android.util.Log
import com.musichub.miuix.data.api.NeteaseApi
import com.musichub.miuix.data.model.SongLyrics
import com.musichub.miuix.data.model.Track
import com.musichub.miuix.ui.theme.ThemeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 多平台歌词来源：
 * - 智能选择：优先使用带逐字信息的歌词，其次网易云 > QQ > 酷狗
 * - 优先某平台：先取该平台，失败时按顺序回退
 * - 自动升级：当前结果的歌词不是逐字时，尝试其他平台获取逐字歌词覆盖
 * - 黑名单：命中后只使用网易云原歌词，不再跨平台升级
 *
 * 各平台原始格式（YRC/QRC/KRC/LRC）统一交给 LyricParser，
 * 以复用无用歌词过滤、歌手行识别等逻辑。
 */
object LyricsRepository {

    private const val TAG = "LyricsRepository"

    private enum class Platform { NETEASE, QQ, KUGOU }

    /** 歌词加载结果，[source] 为需要展示的来源名称（网易云为 null，不展示）。 */
    data class LyricsLoadResult(
        val lyrics: SongLyrics,
        val source: String?,
        val upgraded: Boolean
    )

    private data class LoadOutcome(
        val result: PlatformLyricResult,
        val upgraded: Boolean
    )

    suspend fun load(track: Track): Result<LyricsLoadResult> = withContext(Dispatchers.IO) {
        try {
            // 黑名单歌曲：只使用网易云原歌词，不跨平台升级
            if (LyricUpgradeBlacklist.contains(track.id)) {
                val netease = fetch(Platform.NETEASE, track)
                    ?: return@withContext Result.failure(Exception("未找到歌词"))
                return@withContext Result.success(
                    LyricsLoadResult(
                        lyrics = netease.lyrics,
                        source = platformLabel(netease.platform),
                        upgraded = false
                    )
                )
            }

            // 需要升级列表：强制优先其他平台的逐字歌词（即使网易云已有逐字歌词）
            val forceUpgrade = LyricUpgradeRequiredList.contains(track.id)
            val preference = ThemeSettings.lyricSourcePreferenceIndex
            val outcome = if (preference == 0) {
                loadSmart(track, forceUpgrade)
            } else {
                loadPreferred(track, preference, forceUpgrade)
            }

            if (outcome != null) {
                Result.success(
                    LyricsLoadResult(
                        lyrics = outcome.result.lyrics,
                        source = platformLabel(outcome.result.platform),
                        upgraded = outcome.upgraded || outcome.result.platform != "netease"
                    )
                )
            } else {
                Result.failure(Exception("未找到歌词"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "load failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun platformLabel(platform: String): String = when (platform) {
        "qq" -> "QQ音乐"
        "kugou" -> "酷狗音乐"
        else -> "网易云音乐"
    }

    /** 日文假名检测，用于判断是否需要保留网易云罗马音。 */
    private val kanaRegex = Regex("[\\u3040-\\u30FF]")

    private fun japaneseNoUpgradeActive(): Boolean =
        ThemeSettings.showJapaneseRomaji && ThemeSettings.japaneseNoUpgrade

    private fun isJapaneseLyrics(lyrics: SongLyrics): Boolean =
        lyrics.lines.any { kanaRegex.containsMatchIn(it.text) }

    /** 智能选择：优先网易云；开启自动升级时，若无逐字歌词则尝试其他平台。 */
    private suspend fun loadSmart(track: Track, forceUpgrade: Boolean): LoadOutcome? {
        val wantWordLyrics = forceUpgrade ||
            (ThemeSettings.wordByWordLyrics && ThemeSettings.autoUpgradeLyrics)
        val netease = fetch(Platform.NETEASE, track)

        // 日语歌曲不升级：保留网易云歌词，确保罗马音可用
        if (!forceUpgrade && netease != null && japaneseNoUpgradeActive() &&
            isJapaneseLyrics(netease.lyrics)
        ) {
            Log.d(TAG, "Japanese lyrics: skip upgrade to keep Netease romanization")
            return LoadOutcome(netease, upgraded = false)
        }

        // 强制升级时不因网易云已有逐字歌词而提前返回
        if (!forceUpgrade && netease != null && (!wantWordLyrics || netease.hasWordLines)) {
            return LoadOutcome(netease, upgraded = false)
        }

        val others = coroutineScope {
            val qq = if (ThemeSettings.qqLyricsEnabled) async { fetch(Platform.QQ, track) } else null
            val kugou = if (ThemeSettings.kugouLyricsEnabled) async { fetch(Platform.KUGOU, track) } else null
            listOfNotNull(qq?.await(), kugou?.await())
        }.filterNotNull()

        if (wantWordLyrics) {
            others.firstOrNull { it.hasWordLines }?.let {
                return LoadOutcome(it, upgraded = true)
            }
        }
        val fallback = netease ?: others.firstOrNull() ?: return null
        return LoadOutcome(fallback, upgraded = false)
    }

    /** 优先指定平台，失败时按顺序回退；可按设置升级为逐字歌词。 */
    private suspend fun loadPreferred(track: Track, preference: Int, forceUpgrade: Boolean): LoadOutcome? {
        val order = platformOrder(preference)
        val upgrade = forceUpgrade ||
            (ThemeSettings.wordByWordLyrics && ThemeSettings.autoUpgradeLyrics)

        var first: PlatformLyricResult? = null
        var firstPlatform: Platform? = null
        for (platform in order) {
            val result = fetch(platform, track)
            if (result != null) {
                first = result
                firstPlatform = platform
                break
            }
        }
        if (first == null) return null

        // 日语歌曲不升级：保留当前首选平台的歌词
        if (!forceUpgrade && japaneseNoUpgradeActive() && isJapaneseLyrics(first.lyrics)) {
            Log.d(TAG, "Japanese lyrics: skip cross-platform upgrade")
            return LoadOutcome(first, upgraded = false)
        }

        if (upgrade && (forceUpgrade || !first.hasWordLines)) {
            for (platform in order) {
                if (platform == firstPlatform) continue
                val upgraded = fetch(platform, track)
                if (upgraded != null && upgraded.hasWordLines) {
                    Log.d(TAG, "Upgraded lyrics from ${platform.name} (word-by-word)")
                    return LoadOutcome(upgraded, upgraded = true)
                }
            }
        }
        return LoadOutcome(first, upgraded = false)
    }

    private fun platformOrder(preference: Int): List<Platform> {
        val base = when (preference) {
            2 -> listOf(Platform.QQ, Platform.NETEASE, Platform.KUGOU)
            3 -> listOf(Platform.KUGOU, Platform.NETEASE, Platform.QQ)
            else -> listOf(Platform.NETEASE, Platform.QQ, Platform.KUGOU)
        }
        return base.filter { isEnabled(it) }.ifEmpty { listOf(Platform.NETEASE) }
    }

    private fun isEnabled(platform: Platform): Boolean = when (platform) {
        Platform.NETEASE -> true
        Platform.QQ -> ThemeSettings.qqLyricsEnabled
        Platform.KUGOU -> ThemeSettings.kugouLyricsEnabled
    }

    private suspend fun fetch(platform: Platform, track: Track): PlatformLyricResult? {
        return when (platform) {
            Platform.NETEASE -> {
                NeteaseApi.getSongLyric(
                    songId = track.id,
                    songTitle = track.title,
                    songArtist = track.artist,
                    songAlbum = track.album,
                    songDuration = track.duration,
                    artistNames = track.artistNames
                ).getOrNull()?.let { lyrics ->
                    PlatformLyricResult(
                        lyrics = lyrics,
                        hasWordLines = lyrics.lines.any { !it.words.isNullOrEmpty() },
                        platform = "netease"
                    )
                }
            }
            Platform.QQ -> QqMusicLyricsApi.fetch(track)
            Platform.KUGOU -> KugouLyricsApi.fetch(track)
        }
    }
}
