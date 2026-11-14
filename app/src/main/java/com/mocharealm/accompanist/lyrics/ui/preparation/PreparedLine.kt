package com.mocharealm.accompanist.lyrics.ui.preparation

import androidx.compose.ui.text.TextLayoutResult
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine

class PreparedLine
internal constructor(
    val source: KaraokeLine,
    val runs: List<PreparedProfileRun>,
    val rows: List<PreparedRow>,
    val width: Float,
    val height: Float,
    val rightAligned: Boolean,
    val before: List<PreparedLine>,
    val after: List<PreparedLine>,
    val translation: TextLayoutResult?,
    val phonetic: TextLayoutResult?,
) {
    internal var visibilityStart: Int = source.start
        private set

    internal var visibilityEnd: Int = source.end
        private set

    internal var revealFromBottom: Boolean = false
        private set

    init {
        val groupEnd = maxOf(source.end, (before + after).maxOfOrNull { it.source.end } ?: source.end)
        for (child in before + after) {
            // Late backing vocals appear with their owner; early vocals reveal at their own cue.
            child.visibilityStart = minOf(source.start, child.source.start)
            child.visibilityEnd = groupEnd
            child.revealFromBottom = child.source.start < source.start
        }
    }
}
