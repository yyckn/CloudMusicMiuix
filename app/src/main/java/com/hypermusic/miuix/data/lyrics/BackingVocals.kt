package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.LyricWord

/** AMLL-specific classification; other providers never opt in merely by having parentheses. */
internal object BackingVocals {
    fun isWrapped(text: String): Boolean {
        val value = text.trim()
        if (value.length <= 2 || value.first() != '(' || value.last() != ')') return false
        var depth = 0
        for ((index, char) in value.withIndex()) {
            if (char == '(') depth++
            if (char == ')') depth--
            if (depth < 0 || (depth == 0 && index != value.lastIndex)) return false
        }
        return depth == 0 && value.substring(1, value.lastIndex).isNotBlank()
    }

    fun displayText(text: String): String = if (isWrapped(text)) text.trim().drop(1).dropLast(1).trim() else text

    /** Remove wrapper glyphs even when '(' and ')' have their own timed spans; keep all timings. */
    fun displayWords(words: List<LyricWord>): List<LyricWord> {
        val text = words.joinToString("") { it.text }
        if (!isWrapped(text)) return words
        val first = text.indexOf('(') + 1
        val last = text.lastIndexOf(')')
        var offset = 0
        return words.mapNotNull { word ->
            val begin = maxOf(first - offset, 0)
            val end = minOf(last - offset, word.text.length)
            offset += word.text.length
            if (begin >= end) null else word.copy(text = word.text.substring(begin, end))
        }
    }
}
