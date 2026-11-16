package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.SongLyrics
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.ui.theme.ThemeSettings

/** Provider text before exclusion, singer-line handling or other rendering transformations. */
data class RawLyricDocument(
    val format: String, val original: String, val translation: String = "",
    val wordLyric: String = "", val romanization: String = "",
    val wordTranslation: String = "", val wordRomanization: String = "",
    val providerOriginal: String = "", val providerTranslation: String = ""
) {
    fun parse(track: Track): SongLyrics {
        val roman = if (ThemeSettings.showJapaneseRomaji) romanization else ""
        return when (format) {
            "ttml" -> TtmlLyricsParser.parse(original, track, ThemeSettings.showJapaneseRomaji)
            "qrc" -> LyricParser.parseQrc(original, translation, track.title, track.artist, track.album, track.artistNames, roman)
            "krc" -> LyricParser.parseKrc(original, translation, track.title, track.artist, track.album, track.artistNames, roman)
            "bodian" -> BodianLyricsApi.parseLyrics(original, wordLyric, track)
            else -> LyricParser.parse(original, wordTranslation.ifBlank { translation }, wordLyric,
                track.title, track.artist, track.album, track.artistNames,
                if (ThemeSettings.showJapaneseRomaji) wordRomanization.ifBlank { romanization } else "")
        }
    }
}
