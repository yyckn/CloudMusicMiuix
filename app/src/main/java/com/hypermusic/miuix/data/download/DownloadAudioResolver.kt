package com.hypermusic.miuix.data.download

import android.content.Context
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.data.model.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The same authenticated resolution and quality fallback are used by downloads and comparison. */
object DownloadAudioResolver {
    suspend fun resolve(context: Context, entry: DownloadEntry): Triple<String, String, String> = withContext(Dispatchers.IO) {
        val track = entry.track
        require(track.source in setOf("netease", "qqmusic", "bodian", "kugou", "qishui")) { "暂不支持该音乐平台的下载" }
        if (QqMusicApi.accounts.isEmpty()) QqMusicApi.init(context)
        if (NeteaseApi.getAccounts().isEmpty()) NeteaseApi.init(context)
            return@withContext if (track.source == "bodian") {
                val client = com.hypermusic.miuix.data.api.BodianClient(context, playback = true, playbackAccountIdOverride = entry.accountId)
                check(com.hypermusic.miuix.data.api.AdditionalAccounts(context, com.hypermusic.miuix.data.api.MusicPlatform.BODIAN)
                    .accounts().any { it.id == entry.accountId }) { "下载账号已移除，请重新下载" }
                val requested = when (entry.qualityIndex) {
                    0 -> com.hypermusic.miuix.data.api.BodianPlayback.Quality.LOSSLESS
                    1 -> com.hypermusic.miuix.data.api.BodianPlayback.Quality.HIGH
                    else -> com.hypermusic.miuix.data.api.BodianPlayback.Quality.STANDARD
                }
                val audio = com.hypermusic.miuix.data.api.BodianPlayback(client).resolve(track, requested)
                check(audio.preview == null) { "该歌曲只提供试听片段，不能下载完整歌曲" }
                check(com.hypermusic.miuix.data.api.AdditionalAccounts(context, com.hypermusic.miuix.data.api.MusicPlatform.BODIAN)
                    .accounts().any { it.id == entry.accountId }) { "下载账号已移除，请重新下载" }
                Triple(audio.url, requested.format, "未知音质")
            } else if (track.source == "qishui") {
                val api = com.hypermusic.miuix.data.api.QishuiClient(context, playback = true)
                val auth = com.hypermusic.miuix.data.api.QishuiAuth(context, api)
                val revision = auth.sessionGeneration()
                check(auth.account(playback = true)?.userId == entry.accountId) { "下载账号已切换，请重新下载" }
                val requested = when (entry.qualityIndex) {
                    0 -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.HIRES
                    1 -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.LOSSLESS
                    2 -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.HIGH
                    else -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.STANDARD
                }
                val detail = com.hypermusic.miuix.data.api.QishuiCatalog(api).songDetail(track.id.removePrefix("qishui_"))
                val audio = com.hypermusic.miuix.data.api.QishuiPlayback(api).resolve(detail, requested)
                check(!audio.preview) { "该歌曲只提供试听片段，不能下载完整歌曲" }
                check(auth.sessionGeneration() == revision && auth.account(playback = true)?.userId == entry.accountId) { "下载账号已切换，请重新下载" }
                Triple(audio.url, "bin", audio.qualityLabel())
            } else if (track.source == "kugou") {
                val api = com.hypermusic.miuix.data.api.KugouClient(context)
                val auth = com.hypermusic.miuix.data.api.KugouAuth(context, api)
                val revision = auth.sessionGeneration()
                check(auth.account(playback = true)?.userId == entry.accountId) { "下载账号已切换，请重新下载" }
                val requested = when (entry.qualityIndex) {
                    0 -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.HIRES
                    1 -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.LOSSLESS
                    2 -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.HIGH
                    else -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.STANDARD
                }
                val audio = com.hypermusic.miuix.data.api.KugouPlayback(api, auth).resolve(track, requested)
                check(!audio.preview) { "该歌曲只提供试听片段，不能下载完整歌曲" }
                check(auth.sessionGeneration() == revision && auth.account(playback = true)?.userId == entry.accountId) { "下载账号已切换，请重新下载" }
                val suffix = audio.extension ?: runCatching { java.net.URI(audio.url).path.substringAfterLast('.', "").lowercase() }.getOrDefault("")
                    .takeIf { it in setOf("mp3", "flac", "m4a", "aac", "ogg", "wav") } ?: "bin"
                Triple(audio.url, suffix, audio.qualityLabel())
            } else if (track.source == "qqmusic") {
                if (entry.accountId != null) check(QqMusicApi.accounts.any { it.credential.musicId == entry.accountId }) { "下载账号已移除，请重新下载" }
                val audio = QqMusicApi.getSongAudio(track.id, track.platform.qqMediaMid, entry.qualityIndex, entry.accountId).getOrThrow()
                Triple(audio.url, audio.extension, audio.quality)
            } else if (track.source == "netease") {
                val cookie = entry.accountId?.let { NeteaseApi.getAccountCookie(it) ?: error("下载账号已移除，请重新下载") }
                val (url, bitrate, type) = NeteaseApi.getSongUrl(track.id, AudioQuality.neteaseBitrate(entry.qualityIndex), accountCookie = cookie).getOrThrow()
                Triple(url, type.ifBlank { "mp3" }, AudioQuality.neteaseActual(bitrate, type))
            } else error("暂不支持该音乐平台的下载")
    }
}
