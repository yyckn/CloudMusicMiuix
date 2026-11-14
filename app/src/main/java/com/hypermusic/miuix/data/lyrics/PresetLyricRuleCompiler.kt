package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.lyrics.LyricExclusionRules.MatchType
import com.hypermusic.miuix.data.lyrics.LyricExclusionRules.Rule

/** 预置清单的数据结构；ID 与条目位置无关，供用户保存单条规则的开关状态。 */
internal data class PresetLyricRule(
    val id: String,
    val text: String,
    val maxDurationMs: Long = 0L,
)

/** 将可读清单转换为匹配引擎使用的规则，不在这里自动合并或删减条目。 */
internal object PresetLyricRuleCompiler {
    private const val COLON_SUFFIX = "\\s*[:：﹕]"

    fun compile(): List<Rule> = with(PresetLyricRules) {
        compileGroup(startWithColon, MatchType.START, withColon = true) +
            compileGroup(start, MatchType.START) +
            compileGroup(anywhereWithColon, MatchType.ANYWHERE, withColon = true) +
            compileGroup(anywhere, MatchType.ANYWHERE) +
            compileGroup(keywords, MatchType.ANYWHERE) +
            compileGroup(durationKeywords, MatchType.ANYWHERE) +
            compileGroup(songInfo, MatchType.ANYWHERE)
    }.also { rules ->
        require(rules.map { it.id }.distinct().size == rules.size) { "预置歌词排除规则 ID 重复" }
    }

    private fun compileGroup(
        entries: List<PresetLyricRule>,
        matchType: MatchType,
        withColon: Boolean = false,
    ): List<Rule> = entries.map { entry ->
        require(entry.id.isNotBlank() && entry.text.isNotBlank()) { "预置歌词排除规则不能为空" }
        require(entry.maxDurationMs >= 0) { "预置歌词排除规则时长不能为负数" }
        Rule(
            id = entry.id,
            pattern = entry.text + if (withColon) COLON_SUFFIX else "",
            durationMs = entry.maxDurationMs,
            matchType = matchType,
        )
    }
}
