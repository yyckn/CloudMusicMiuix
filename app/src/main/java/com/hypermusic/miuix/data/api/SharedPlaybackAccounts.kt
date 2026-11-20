package com.hypermusic.miuix.data.api

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.google.gson.Gson
import com.hypermusic.miuix.data.model.Track
import java.text.Normalizer
import java.util.Locale

/** Only playback routing is shared. Data sessions, credentials and account lists stay separate. */
internal object SharedPlaybackAccounts {
    @androidx.annotation.Keep data class Selection(val source: String, val id: String)
    data class Option(val selection: Selection, val label: String)
    val revision = mutableIntStateOf(0)
    private fun prefs(context: Context) = context.getSharedPreferences("shared_playback_accounts", Context.MODE_PRIVATE)
    fun supports(source: String) = source == "qqmusic" || source == "bodian"
    fun options(context: Context, source: String): List<Option> {
        if (!supports(source)) return emptyList()
        val qq = QqMusicApi.accounts
        val bodian = AdditionalAccounts(context, MusicPlatform.BODIAN).accounts()
        val qqOptions = qq.map { account ->
            val wx = account.credential.loginType == 1
            val sameName = qq.filter { it.name == account.name }
            val login = if (source == "bodian") {
                if (wx) "QQ音乐-微信" else if (sameName.any { it.credential.loginType == 1 }) "QQ音乐-QQ" else "QQ音乐"
            } else if (wx) "微信" else "QQ"
            val sameType = sameName.count { (it.credential.loginType == 1) == wx } > 1
            Option(Selection("qqmusic", account.credential.musicId),
                account.name + "（$login）" + if (sameType) idSuffix(account.credential.musicId) else "")
        }
        val bodianOptions = bodian.map { account ->
            val name = account.nickname.ifBlank { account.id }
            Option(Selection("bodian", account.id), name + (if (source == "qqmusic") "（波点）" else "") +
                if (bodian.count { it.nickname.ifBlank { it.id } == name } > 1) idSuffix(account.id) else "")
        }
        return if (source == "qqmusic") qqOptions + bodianOptions else bodianOptions + qqOptions
    }
    private fun idSuffix(id: String) = "（ID：${if (id.length > 4) "**${id.takeLast(4)}" else id}）"
    fun selected(context: Context, source: String): Selection? {
        if (!supports(source)) return null
        val enabled = if (source == "qqmusic") context.getSharedPreferences("qqmusic_prefs", Context.MODE_PRIVATE)
            .getBoolean("multi_account_enabled", false) else AdditionalAccounts(context, MusicPlatform.BODIAN).enabled
        if (!enabled) return null
        val stored = prefs(context).getString(source, null) ?: return null
        val selection = runCatching { Gson().fromJson(stored, Selection::class.java) }.getOrNull() ?: return null
        return options(context, source).firstOrNull { it.selection == selection }?.selection
    }
    fun current(context: Context, source: String): Selection? = selected(context, source) ?: when (source) {
        "qqmusic" -> QqMusicApi.playbackAccountId?.let { Selection(source, it) }
        "bodian" -> AdditionalAccounts(context, MusicPlatform.BODIAN).playbackId?.let { Selection(source, it) }
        else -> null
    }
    fun select(context: Context, origin: String, selected: Selection) {
        require(options(context, origin).any { it.selection == selected }) { "账号已移除，请刷新后重试" }
        if (origin == selected.source) {
            if (origin == "qqmusic") check(QqMusicApi.switchPlaybackAccount(selected.id))
            else AdditionalAccounts(context, MusicPlatform.BODIAN).selectPlayback(selected.id)
            check(prefs(context).edit().remove(origin).commit())
        } else check(prefs(context).edit().putString(origin, Gson().toJson(selected)).commit())
        revision.intValue++
    }
    fun isCrossPlatform(context: Context, track: Track): Boolean =
        track.localDownloadId.isNullOrBlank() && selected(context, track.source)?.source?.let { it != track.source } == true

    /** Preserve qualifiers such as live/remix; every credited singer must match. */
    private fun plain(text: String) = text.replace(Regex("</?em\\b[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
    fun sameSong(first: Track, second: Track): Boolean {
        fun normalize(text: String) = Normalizer.normalize(plain(text), Normalizer.Form.NFKC).trim()
            .replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        fun artists(track: Track): Set<String> = (track.artistNames.takeIf { it.isNotEmpty() }
            ?: plain(track.artist).split(Regex("[/、,，;&]"))).map(::normalize).filter(String::isNotEmpty).toSet()
        val names = artists(first)
        return normalize(first.title).isNotEmpty() && normalize(first.title) == normalize(second.title) &&
            names.isNotEmpty() && names == artists(second)
    }
    suspend fun match(context: Context, track: Track, target: String): Track {
        if (track.source == target) return track
        require(supports(track.source) && supports(target))
        val key = "match:${track.source}:${track.id}:$target"
        val cache = context.getSharedPreferences("playback_song_matches", Context.MODE_PRIVATE)
        runCatching { Gson().fromJson(cache.getString(key, null), Track::class.java) }.getOrNull()
            ?.takeIf { it.source == target && sameSong(track, it) }?.let { return it }
        // A downloaded target-platform track also provides a verified identity while offline.
        com.hypermusic.miuix.data.download.OfflineLibrary.available().map { it.track }
            .filter { it.source == target && sameSong(track, it) }
            .minByOrNull { if (track.duration > 0 && it.duration > 0) kotlin.math.abs(track.duration - it.duration) else Long.MAX_VALUE }
            ?.let { return it }
        for (query in listOf("${plain(track.title)} ${plain(track.artist)}", plain(track.title)).distinct()) {
            val candidates = if (target == "qqmusic") QqMusicApi.searchSongs(query).getOrThrow()
                else BodianCatalog(BodianClient(context)).searchSongs(query).items
            candidates.filter { it.source == target && sameSong(track, it) }
                .minByOrNull { if (track.duration > 0 && it.duration > 0) kotlin.math.abs(track.duration - it.duration) else Long.MAX_VALUE }
                ?.let {
                    val matched = it.copy(title = plain(it.title), artist = plain(it.artist), album = plain(it.album),
                        artistNames = it.artistNames.map(::plain), url = null)
                    cache.edit().putString(key, Gson().toJson(matched)).apply()
                    return matched
                }
        }
        error("${if (target == "bodian") "波点音乐" else "QQ音乐"}未找到歌名和歌手完全一致的歌曲")
    }
    fun qualityIndex(origin: String, target: String, index: Int): Int = when {
        origin == target -> index
        target == "bodian" -> when (index) { 0, 1 -> 0; 2 -> 1; else -> 2 }
        target == "qqmusic" -> when (index) { 0 -> 1; 1 -> 2; else -> 3 }
        else -> index
    }

    data class Route(val track: Track, val account: Selection, val qualityIndex: Int)
    suspend fun route(context: Context, track: Track, requested: Int): Route? {
        val account = selected(context, track.source)?.takeIf { it.source != track.source } ?: return null
        val matched = match(context, track, account.source)
        check(selected(context, track.source) == account) { "播放账号已切换，请重试" }
        return Route(matched, account, qualityIndex(track.source, account.source, requested))
    }
    data class Audio(val url: String, val quality: String, val bitrate: Int, val extension: String, val preview: Boolean = false)
    suspend fun audio(context: Context, route: Route): Audio = if (route.account.source == "qqmusic") {
        val audio = QqMusicApi.getSongAudio(route.track.id, route.track.qqMediaMid, route.qualityIndex, route.account.id).getOrThrow()
        Audio(audio.url, audio.quality, audio.bitrate, audio.extension)
    } else {
        val requested = when (route.qualityIndex) { 0 -> BodianPlayback.Quality.LOSSLESS; 1 -> BodianPlayback.Quality.HIGH; else -> BodianPlayback.Quality.STANDARD }
        val audio = BodianPlayback(BodianClient(context, playback = true, playbackAccountIdOverride = route.account.id)).resolve(route.track, requested)
        check(audio.preview == null || audio.preview.startSeconds == 0L) { "该歌曲的试听片段暂不支持播放" }
        Audio(audio.url, "未知音质", 0, audio.requestedQuality.format, audio.preview != null)
    }
}
