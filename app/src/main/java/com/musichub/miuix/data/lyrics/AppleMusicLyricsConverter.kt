package com.musichub.miuix.data.lyrics

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.musichub.miuix.data.model.SongLyrics

/**
 * 将本应用内部的 [SongLyrics] 转换为 accompanist-lyrics-core 的 [SyncedLyrics]，
 * 供 Apple Music 歌词渲染引擎使用。
 */
object AppleMusicLyricsConverter {

    private const val DEFAULT_LINE_DURATION = 5000
    private const val LAST_LINE_DURATION = 10000

    fun convert(
        song: SongLyrics,
        endAligned: Boolean = false,
        wordByWord: Boolean = true
    ): SyncedLyrics {
        val source = song.lines
            .asSequence()
            .filter { !it.isArtistLine && it.text.isNotBlank() }
            .sortedBy { it.startAt }
            .toList()
        if (source.isEmpty()) return SyncedLyrics(emptyList())

        val alignment = if (endAligned) KaraokeAlignment.End else KaraokeAlignment.Start
        val result = ArrayList<ISyncedLine>(source.size)

        for (i in source.indices) {
            val line = source[i]
            val start = line.startAt.toInt()
            val nextStart = source.getOrNull(i + 1)?.startAt?.toInt()
            val end = when {
                line.endAt > line.startAt -> line.endAt.toInt()
                nextStart != null && nextStart > start -> nextStart
                i == source.lastIndex -> start + LAST_LINE_DURATION
                else -> start + DEFAULT_LINE_DURATION
            }.coerceAtLeast(start)
            val translation = line.translation.ifBlank { null }
            val phonetic = line.phonetic.ifBlank { null }

            val syllables = if (wordByWord) {
                line.words
                    ?.asSequence()
                    ?.filter { it.text.isNotEmpty() }
                    ?.map {
                        val s = it.startAt.toInt().coerceIn(start, end)
                        val e = it.endAt.toInt().coerceIn(s, end)
                        KaraokeSyllable(content = it.text, start = s, end = e)
                    }
                    ?.toList()
            } else {
                null
            }

            if (syllables != null && syllables.isNotEmpty()) {
                result.add(
                    KaraokeLine.MainKaraokeLine(
                        syllables = syllables,
                        translation = translation,
                        alignment = alignment,
                        start = start,
                        end = end,
                        phonetic = phonetic,
                    )
                )
            } else if (phonetic != null) {
                // 非逐字歌词也用单音节卡拉OK行承载罗马音，避免 SyncedLine 丢失注音
                result.add(
                    KaraokeLine.MainKaraokeLine(
                        syllables = listOf(
                            KaraokeSyllable(content = line.text, start = start, end = end)
                        ),
                        translation = translation,
                        alignment = alignment,
                        start = start,
                        end = end,
                        phonetic = phonetic,
                    )
                )
            } else {
                result.add(
                    SyncedLine(
                        content = line.text,
                        translation = translation,
                        start = start,
                        end = end,
                    )
                )
            }
        }

        return SyncedLyrics(lines = result)
    }
}
