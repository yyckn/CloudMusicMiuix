package com.hypermusic.miuix.data.lyrics

import android.content.Context
import com.hypermusic.miuix.data.api.BodianClient
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64
import kotlin.math.abs

/** 波点原生歌词；原始逐字时间值需要先解码为行内偏移。 */
internal object BodianLyricsApi {
    private var client: BodianClient? = null
    fun init(context: Context) { if (client == null) client = BodianClient(context.applicationContext) }

    suspend fun fetch(track: Track): PlatformLyricResult? = withContext(Dispatchers.IO) {
        if (track.source != "bodian") return@withContext null
        val id = track.id.removePrefix("bodian_")
        if (!id.matches(Regex("[0-9]{1,20}"))) return@withContext null
        val api = client ?: return@withContext null
        runCatching {
            val query = "type=lyric&req=2&lrcx=1&rid=$id&songname=&artist=&corp=kuwo&fromchannel=bodian"
            val encoded = Base64.getEncoder().encodeToString(query.toByteArray(Charsets.UTF_8))
            val data = api.call("/mobi.s", mapOf("f" to "bodian", "q" to encoded), lyric = true)
                .getAsJsonObject("data") ?: return@runCatching null
            val raw = String(Base64.getDecoder().decode(data.get("content")?.asString.orEmpty()), Charsets.UTF_8)
            val (lrc, krc) = decode(raw)
            val lyrics = parseLyrics(lrc, krc, track)
            if (lyrics.lines.isEmpty() && !lyrics.isPureMusic) null else PlatformLyricResult(lyrics, lyrics.lines.any { !it.words.isNullOrEmpty() },
                "bodian", formatRawLyricSections("波点原歌词" to raw, "解码逐字" to krc), track,
                rawDocument = RawLyricDocument("bodian", lrc, wordLyric = krc, providerOriginal = raw))
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            null
        }
    }

    internal fun parseLyrics(lrc: String, krc: String, track: Track): com.hypermusic.miuix.data.model.SongLyrics {
        val plain = LyricParser.parse(lrc = lrc, tlyric = "", yrc = "", songTitle = track.title,
            songArtist = track.artist, songAlbum = track.album, artistNames = track.artistNames)
        if (krc.isBlank()) return plain
        val timed = LyricParser.parseKrc(krc = krc, tlyric = "", songTitle = track.title,
            songArtist = track.artist, songAlbum = track.album, artistNames = track.artistNames)
        if (timed.lines.isEmpty()) return plain
        if (plain.lines.isEmpty()) return timed
        val timedByKey = timed.lines.filter { !it.words.isNullOrEmpty() }.associateBy { it.startAt to it.text }
        return plain.copy(lines = plain.lines.map { timedByKey[it.startAt to it.text] ?: it })
    }

    internal fun decode(content: String): Pair<String, String> {
        val tag = Regex("\\[kuwo:([0-7]+)]").find(content)?.groupValues?.get(1)
        val factors = tag?.toIntOrNull(8) ?: 11
        val startFactor = factors / 10
        val durationFactor = factors % 10
        val word = Regex("<(-?\\d+),(-?\\d+)>")
        val plain = content.replace(Regex("\\[kuwo:[^]]*]"), "").replace(word, "")
        if (startFactor == 0 || durationFactor == 0) return plain to ""
        val timestamp = Regex("^\\[(\\d+):(\\d+)(?:[.:](\\d{1,3}))?]")
        val lines = content.lineSequence().mapNotNull { line ->
            val time = timestamp.find(line) ?: return@mapNotNull null
            val matches = word.findAll(line.substring(time.range.last + 1)).toList()
            if (matches.isEmpty()) return@mapNotNull null
            val fraction = time.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            val start = time.groupValues[1].toLong() * 60_000 + time.groupValues[2].toLong() * 1000 + fraction
            var duration = 0L
            val body = word.replace(line.substring(time.range.last + 1)) { match ->
                val a = match.groupValues[1].toLongOrNull() ?: 0L
                val b = match.groupValues[2].toLongOrNull() ?: 0L
                val offset = abs((a + b) / (2L * startFactor))
                val length = abs((a - b) / (2L * durationFactor))
                duration = maxOf(duration, offset + length)
                "<$offset,$length,0>"
            }
            "[$start,$duration]$body"
        }.joinToString("\n")
        return plain to lines
    }
}
