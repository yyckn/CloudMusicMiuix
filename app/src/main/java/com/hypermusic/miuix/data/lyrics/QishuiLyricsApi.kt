package com.hypermusic.miuix.data.lyrics

import android.content.Context
import com.google.gson.JsonObject
import com.hypermusic.miuix.data.api.QishuiCatalog
import com.hypermusic.miuix.data.api.QishuiClient
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object QishuiLyricsApi {
    private var catalog: QishuiCatalog? = null
    fun init(context: Context) { if (catalog == null) catalog = QishuiCatalog(QishuiClient(context.applicationContext)) }

    suspend fun fetch(track: Track): PlatformLyricResult? = withContext(Dispatchers.IO) {
        if (track.source != "qishui") return@withContext null
        val api = catalog ?: return@withContext null
        try {
            val details = api.songDetail(track.id.removePrefix("qishui_"))
            val info = details.getAsJsonObject("display_info")?.getAsJsonObject("lyric_info") ?: return@withContext null
            parse(info, track)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
    }

    internal fun parse(info: JsonObject, track: Track): PlatformLyricResult? {
        val entity = info.getAsJsonObject("lyric_entity") ?: return null
        val content = entity.text("content").replace("\u0000", "")
        if (content.isBlank()) return null
        val translations = info.getAsJsonObject("lang_translations")
        val translation = translations?.entrySet()?.asSequence()
            ?.filter { it.value.isJsonObject }
            ?.sortedBy { entry ->
                when (entry.key.replace('_', '-').lowercase(java.util.Locale.ROOT)) {
                    "zh-cn", "zh-hans-cn", "zh-hans" -> 0
                    "zh", "zh-sg", "zh-hans-sg" -> 1
                    "zh-tw", "zh-hk", "zh-hant", "zh-hant-tw", "zh-hant-hk" -> 2
                    else -> 3
                }
            }?.firstOrNull { it.key.replace('_', '-').lowercase(java.util.Locale.ROOT).let { key -> key == "zh" || key.startsWith("zh-") } }
            ?.value?.asJsonObject
        val text = translation?.text("content").orEmpty().replace("\u0000", "")
        val plainTranslation = if (translation?.text("type").equals("krc", ignoreCase = true)) krcToLrc(text) else text
        val lyrics = if (entity.text("type").equals("krc", ignoreCase = true)) {
            LyricParser.parseKrc(krc = content, tlyric = plainTranslation, songTitle = track.title,
                songArtist = track.artist, songAlbum = track.album, artistNames = track.artistNames)
        } else LyricParser.parse(lrc = content, tlyric = plainTranslation, yrc = "", songTitle = track.title,
            songArtist = track.artist, songAlbum = track.album, artistNames = track.artistNames)
        if (lyrics.lines.isEmpty() && !lyrics.isPureMusic) return null
        return PlatformLyricResult(lyrics, lyrics.lines.any { !it.words.isNullOrEmpty() }, "qishui",
            formatRawLyricSections("汽水原歌词" to content, "翻译" to text), track,
            rawDocument = RawLyricDocument(if (entity.text("type").equals("krc", true)) "krc" else "lrc", content, plainTranslation, providerTranslation = text))
    }

    private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun krcToLrc(text: String): String {
        val header = Regex("^\\[(\\d+),(\\d+)]")
        val marker = Regex("<\\d+,\\d+(?:,[-\\d]+)?>")
        return text.lineSequence().mapNotNull { line ->
            val match = header.find(line) ?: return@mapNotNull null
            val time = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val content = line.substring(match.range.last + 1).replace(marker, "")
            "[%02d:%02d.%03d]%s".format(java.util.Locale.ROOT, time / 60_000, time / 1000 % 60, time % 1000, content)
        }.joinToString("\n")
    }
}
