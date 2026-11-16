package com.hypermusic.miuix.data.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.hypermusic.miuix.data.lyrics.LyricsRepository
import com.hypermusic.miuix.data.lyrics.RawLyricDocument
import com.hypermusic.miuix.data.model.Track
import java.io.File

/** Permanent download assets, deliberately outside all evictable image/audio caches. */
object OfflineLibrary {
    private lateinit var context: Context
    private val gson = Gson()
    private val repairMutex = kotlinx.coroutines.sync.Mutex()
    data class SavedLyrics(val raw: RawLyricDocument, val source: String?, val matchedTrack: Track?,
        val upgraded: Boolean, val debugText: String, val selectionKey: String = "")
    fun init(ctx: Context) { context = ctx.applicationContext }
    private fun folder(id: String): File {
        require(id.matches(Regex("[a-fA-F0-9-]{36}")))
        return context.filesDir.resolve("downloads/assets/$id")
    }
    fun find(track: Track): DownloadEntry? {
        val candidates = versions(track)
        return candidates.firstOrNull { it.id == track.localDownloadId } ?: candidates.firstOrNull()
    }
    fun available() = Downloads.entries.filter { it.state == "complete" && File(it.path).isFile }
    fun versions(track: Track) = available().filter { sameDownloadSong(it.track, track) }
    fun localTrack(entry: DownloadEntry) = entry.track.withStorageDefaults().copy(localDownloadId = entry.id, url = null)
    fun cover(entry: DownloadEntry, thumbnail: Boolean = false): File? {
        if (!::context.isInitialized) return null
        return runCatching { folder(entry.id).resolve(if (thumbnail) "thumbnail.jpg" else "cover.original").takeIf(File::isFile) }.getOrNull()
    }
    fun cover(track: Track, thumbnail: Boolean = false) = find(track)?.let { cover(it, thumbnail) }
    fun coverForUrl(url: String): File? = Downloads.entries.firstOrNull { it.state == "complete" && it.track.imgUrl == url }?.let { cover(it) }
    private fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(file)) { "无法保存离线资料" }
    }
    private fun selectionKey(track: Track): String {
        val selection = com.hypermusic.miuix.data.lyrics.LyricMatches.find(track)
        val settings = com.hypermusic.miuix.ui.theme.ThemeSettings
        return "${selection?.platform}:${selection?.match?.source}:${selection?.match?.id}:${settings.lyricSourcePreferenceIndex}:${settings.autoUpgradeLyrics}:${settings.wordByWordLyrics}:${settings.showJapaneseRomaji}"
    }
    fun hasNetwork(): Boolean {
        if (!::context.isInitialized) return false
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }
    fun saveLyrics(track: Track, result: LyricsRepository.LyricsLoadResult) {
        // Matching belongs to the song, so keep every downloaded quality/version in sync.
        versions(track).forEach { entry -> runCatching { writeLyrics(entry, result) } }
    }
    private fun writeLyrics(entry: DownloadEntry, result: LyricsRepository.LyricsLoadResult) {
        val raw = result.rawDocument ?: return
        write(folder(entry.id).resolve("lyrics.json"), gson.toJson(SavedLyrics(raw, result.source,
            result.matchedTrack, result.upgraded, result.rawDebugText, selectionKey(entry.track))))
    }
    fun save(entry: DownloadEntry, coverBytes: ByteArray?, lyrics: LyricsRepository.LyricsLoadResult?): List<String> {
        val warnings = mutableListOf<String>()
        val directory = folder(entry.id).apply { mkdirs() }
        write(directory.resolve("track.json"), gson.toJson(entry.track.withStorageDefaults().copy(url = null, localDownloadId = "")))
        if (coverBytes != null) runCatching {
            directory.resolve("cover.original").writeBytes(coverBytes)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, opts)
            check(opts.outWidth > 0 && opts.outHeight > 0)
            val decode = BitmapFactory.Options().apply {
                inSampleSize = (maxOf(opts.outWidth, opts.outHeight) / 256).coerceAtLeast(1)
            }
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size, decode))
            try { directory.resolve("thumbnail.jpg").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
            finally { bitmap.recycle() }
        }.onFailure { warnings += "离线封面保存失败" }
        if (lyrics?.rawDocument != null) {
            writeLyrics(entry, lyrics)
        } else warnings += "原始歌词不可用，离线时无法显示歌词"
        return warnings
    }
    fun loadLyrics(track: Track): LyricsRepository.LyricsLoadResult? {
        if (!::context.isInitialized) return null
        val entry = find(track) ?: return null
        return runCatching {
            val file = folder(entry.id).resolve("lyrics.json")
            if (!file.isFile) return null
            val saved = gson.fromJson(file.readText(), SavedLyrics::class.java)
            if (saved.selectionKey != selectionKey(track) && hasNetwork()) return null
            LyricsRepository.LyricsLoadResult(saved.raw.parse(track), saved.source, saved.upgraded,
                saved.debugText, saved.matchedTrack, saved.raw)
        }.getOrNull()
    }
    fun needsRepair(entry: DownloadEntry): Boolean = runCatching {
        val dir = folder(entry.id)
        listOf("track.json", "lyrics.json", "cover.original", "thumbnail.jpg").any { !dir.resolve(it).isFile } ||
            entry.format.isBlank() || entry.bitrate <= 0 || entry.track.duration <= 0
    }.getOrDefault(false)

    /** Preserve existing audio and completed assets; supplement only the missing information. */
    suspend fun upgradeLegacy(entry: DownloadEntry, allowNetwork: Boolean = true) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        repairMutex.lock()
        try {
            val current = Downloads.entries.find { it.id == entry.id } ?: return@withContext
            val original = current.track.withStorageDefaults()
            val file = File(current.path)
            if (!file.isFile) return@withContext
            val dir = folder(current.id)
            val audio = runCatching { org.jaudiotagger.audio.AudioFileIO.read(file) }.getOrNull()
            val tag = audio?.tag
            fun tagText(key: org.jaudiotagger.tag.FieldKey) = runCatching { tag?.getFirst(key) }.getOrNull().orEmpty()
            val track = original.copy(
                title = original.title.ifBlank { tagText(org.jaudiotagger.tag.FieldKey.TITLE) },
                artist = original.artist.ifBlank { tagText(org.jaudiotagger.tag.FieldKey.ARTIST) },
                album = original.album.ifBlank { tagText(org.jaudiotagger.tag.FieldKey.ALBUM) },
                duration = original.duration.takeIf { it > 0 } ?: ((audio?.audioHeader?.trackLength ?: 0).toLong() * 1000)
            )
            val format = current.format.ifBlank { file.extension.lowercase() }
            val bitrate = current.bitrate.takeIf { it > 0 } ?: ((audio?.audioHeader?.bitRateAsNumber ?: 0).coerceIn(0L, Int.MAX_VALUE.toLong() / 1000) * 1000).toInt()
            Downloads.update(current.id) { it.copy(track = track, bytes = file.length(), format = format, bitrate = bitrate) }
            val repaired = current.copy(track = track, format = format, bitrate = bitrate)
            val existingCover = cover(current)?.let { runCatching { it.readBytes() }.getOrNull() }
            val embeddedCover = runCatching { tag?.firstArtwork?.binaryData }.getOrNull()
            val coverBytes = existingCover ?: embeddedCover ?: if (allowNetwork && hasNetwork() && track.imgUrl.isNotBlank()) runCatching {
                okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(track.imgUrl).build()).execute().use {
                    if (it.isSuccessful) it.body?.bytes() else null
                }
            }.getOrNull() else null
            val lyricsFile = dir.resolve("lyrics.json")
            val lyrics = if (!lyricsFile.isFile && allowNetwork && hasNetwork()) LyricsRepository.load(track, preferDownload = false).getOrNull() else null
            if (Downloads.entries.none { it.id == current.id }) return@withContext
            val warnings = save(repaired, coverBytes, lyrics).toMutableList()
            if (lyricsFile.isFile) warnings.remove("原始歌词不可用，离线时无法显示歌词")
            if (coverBytes == null) warnings += "离线封面暂不可用，联网后自动补全"
            if (Downloads.entries.none { it.id == current.id }) { remove(current.id); return@withContext }
            val repairMessages = setOf("原始歌词不可用，离线时无法显示歌词", "离线封面保存失败", "离线封面暂不可用，联网后自动补全")
            Downloads.update(current.id) { latest ->
                val retained = latest.message.split("；").filter { it.isNotBlank() && it !in repairMessages && !it.startsWith("离线资料补全失败：") }
                latest.copy(message = (retained + warnings).distinct().joinToString("；"))
            }
        } finally { repairMutex.unlock() }
    }
    fun remove(id: String) { if (::context.isInitialized) folder(id).deleteRecursively() }
}

fun exportedSongName(track: Track, extension: String): String {
    val base = "${track.title} - ${track.artist}".replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.')
    return "${base.take(180).ifBlank { "歌曲" }}.${extension.lowercase().replace(Regex("[^a-z0-9]"), "").ifBlank { "bin" }}"
}
