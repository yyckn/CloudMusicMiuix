package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.LyricLine

/** Keeps original neighbors until singer splitting and word parsing have finished. */
internal class LyricExclusionPass(
    private val title: String,
    private val artists: List<String>,
    private val album: String
) {
    private data class OriginalLine(val time: Long, val outputOffset: Int, val excluded: Boolean)
    private val originals = mutableListOf<OriginalLine>()

    fun shouldExclude(text: String, duration: Long, time: Long, outputOffset: Int): Boolean {
        val excluded = LyricExclusionRules.shouldExclude(text, duration, title, artists, album)
        originals += OriginalLine(time, outputOffset, excluded)
        return excluded
    }

    fun filter(lines: List<LyricLine>): List<LyricLine> {
        val sorted = originals.indices.sortedBy { originals[it].time }
        val expanded = LyricExclusionRules.includeExcludedNeighbors(sorted.map { originals[it].excluded })
        val removed = BooleanArray(lines.size)
        sorted.forEachIndexed { sortedIndex, originalIndex ->
            if (expanded[sortedIndex] && !originals[originalIndex].excluded) {
                val start = originals[originalIndex].outputOffset
                val end = originals.getOrNull(originalIndex + 1)?.outputOffset ?: lines.size
                // Remove the whole original line, including any singer/body split, not its neighbors.
                for (index in start until end) removed[index] = true
            }
        }
        return lines.filterIndexed { index, _ -> !removed[index] }
    }
}
