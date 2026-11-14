package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.LyricLine

/** Remove only paired decorative outer music notes; retain internal notes and all timing data. */
internal object LyricTextCleanup {
    private fun bounds(text: String): Pair<Int, Int>? {
        var start = 0
        var end = text.length
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (end - start < 2 || text[start] != '♪' || text[end - 1] != '♪') return null
        do {
            start++; end--
            while (start < end && text[start].isWhitespace()) start++
            while (end > start && text[end - 1].isWhitespace()) end--
        } while (end - start >= 2 && text[start] == '♪' && text[end - 1] == '♪')
        return start to end
    }

    private fun strip(text: String): String = bounds(text)?.let { (start, end) -> text.substring(start, end) } ?: text

    fun removeOuterMusicNotes(line: LyricLine): LyricLine {
        val text = strip(line.text)
        val words = line.words?.let { words ->
            val range = bounds(words.joinToString("") { it.text }) ?: return@let words
            var offset = 0
            words.mapNotNull { word ->
                val start = maxOf(offset, range.first)
                val end = minOf(offset + word.text.length, range.second)
                val previousOffset = offset
                offset += word.text.length
                if (start >= end) null else word.copy(text = word.text.substring(start - previousOffset, end - previousOffset))
            }
        }
        return line.copy(text = text, words = words?.takeIf { it.isNotEmpty() },
            translation = strip(line.translation), phonetic = strip(line.phonetic))
    }
}
