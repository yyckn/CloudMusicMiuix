package com.hypermusic.miuix.data.lyrics

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.hypermusic.miuix.data.model.LyricLine
import com.hypermusic.miuix.data.model.SongLyrics
import com.mocharealm.accompanist.lyrics.ui.internal.text.isRtl

/** Converts lyric data without changing syllable clocks; AMLL backing vocals share their owner's item. */
object AppleMusicLyricsConverter {
    private const val DEFAULT_LINE_DURATION = 5000
    private const val LAST_LINE_DURATION = 10000

    fun convert(song: SongLyrics, endAligned: Boolean = false, wordByWord: Boolean = true): SyncedLyrics {
        // Group before sorting: an x-bg span can overlap its owner and must not attach to a later p.
        val source = song.lines.filter { !it.isArtistLine && it.text.isNotBlank() }
        val owners = mutableListOf<LyricLine>()
        val children = mutableMapOf<Int, MutableList<LyricLine>>()
        var owner = -1
        for (line in source) {
            if (line.isBackingVocal && owner >= 0) children.getOrPut(owner) { mutableListOf() }.add(line)
            else {
                owners += line
                if (!line.isBackingVocal) owner = owners.lastIndex
            }
        }
        val order = owners.indices.sortedBy { owners[it].startAt }
        val defaultAlignment = if (endAligned) KaraokeAlignment.End else KaraokeAlignment.Start
        val result = ArrayList<ISyncedLine>(owners.size)
        for ((position, index) in order.withIndex()) {
            val line = owners[index]
            val duetRight = when (line.amllAgent) { "v1" -> false; "v2" -> true; else -> null }
            // The engine's Start/End are relative to text direction; duet sides are physical left/right.
            fun alignmentFor(value: LyricLine): KaraokeAlignment = if (duetRight == null) defaultAlignment
                else if (duetRight != value.text.isRtl()) KaraokeAlignment.End else KaraokeAlignment.Start
            val nextStart = order.getOrNull(position + 1)?.let { owners[it].startAt.toInt() }
            val defaultDuration = if (position == order.lastIndex) LAST_LINE_DURATION else DEFAULT_LINE_DURATION
            fun endOf(value: LyricLine): Int {
                val start = value.startAt.toInt()
                val explicitEnd = value.endAt.toInt()
                return when {
                    explicitEnd > start -> explicitEnd
                    nextStart != null && nextStart > start -> nextStart
                    else -> start + defaultDuration
                }
            }
            fun syllables(value: LyricLine): List<KaraokeSyllable> {
                val start = value.startAt.toInt()
                val end = endOf(value)
                val words = value.words.orEmpty().let { if (value.isBackingVocal) BackingVocals.displayWords(it) else it }
                val timed = if (wordByWord) words.filter { it.text.isNotEmpty() }.map {
                    val s = it.startAt.toInt().coerceIn(start, end)
                    KaraokeSyllable(it.text, s, it.endAt.toInt().coerceIn(s, end))
                } else emptyList()
                return timed.ifEmpty {
                    listOf(KaraokeSyllable(if (value.isBackingVocal) BackingVocals.displayText(value.text) else value.text, start, end))
                }
            }
            fun translation(value: LyricLine): String? = value.translation.takeIf {
                it.isNotBlank() && !it.trim().all { c -> c == '/' || c == '／' }
            }?.let { if (value.isBackingVocal) BackingVocals.displayText(it) else it }
            fun accompaniment(value: LyricLine) = KaraokeLine.AccompanimentKaraokeLine(
                syllables(value), translation(value), alignmentFor(value), value.startAt.toInt(), endOf(value),
                value.phonetic.takeIf { it.isNotBlank() }?.let(BackingVocals::displayText)
            )
            val backing = children[index].orEmpty().map(::accompaniment)
            if (line.isBackingVocal) {
                // A leading standalone backing line has no preceding owner; retain it at its own time.
                result += accompaniment(line)
            } else if (duetRight != null || backing.isNotEmpty() || (wordByWord && !line.words.isNullOrEmpty()) || line.phonetic.isNotBlank()) {
                result += KaraokeLine.MainKaraokeLine(
                    syllables(line), translation(line), alignmentFor(line), line.startAt.toInt(), endOf(line),
                    line.phonetic.ifBlank { null }, backing.takeIf { it.isNotEmpty() }
                )
            } else {
                result += SyncedLine(content = line.text, translation = translation(line), start = line.startAt.toInt(), end = endOf(line))
            }
        }
        return SyncedLyrics(result)
    }
}
