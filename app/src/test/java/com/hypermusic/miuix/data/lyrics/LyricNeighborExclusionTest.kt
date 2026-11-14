package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class LyricNeighborExclusionTest {
    @Test fun studioSuffixAndVocalEngineerPrefix() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            fun excluded(text: String) = LyricExclusionRules.shouldExclude(text, 3000, "", emptyList(), "")
            assertTrue(excluded("某某录音棚"))
            assertTrue(excluded("某某录音棚里的回忆"))
            for (colon in listOf(":", "：", "﹕")) {
                assertTrue(excluded("人声录音师 $colon 某人"))
                assertTrue(excluded("这是人声录音师 $colon 正文"))
            }
            assertEquals(emptyList<String>(), LyricExclusionRules.tags(LyricExclusionRules.defaultRules.first { it.id == "default-studio-suffix" }))
            assertEquals(listOf("特殊规则"), LyricExclusionRules.tags(LyricExclusionRules.defaultRules.first { it.id == NEIGHBOR_ID }))
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun allFormatsUseOriginalNeighborsAndRespectTheSwitch() {
        val enabled = LyricExclusionRules.enabled
        val ruleEnabled = LyricExclusionRules.isDefaultRuleEnabled(NEIGHBOR_ID)
        val texts = listOf("前方正常", "作词：某人", "这句需要排除", "录音：某人", "后方正常")
        val lrc = texts.mapIndexed { i, text -> "[00:0${i + 1}]$text" }.joinToString("\n")
        val qrc = texts.mapIndexed { i, text -> "[${(i + 1) * 1000},1000]$text(${(i + 1) * 1000},1000)" }.joinToString("\n")
        val yrc = texts.mapIndexed { i, text -> "[${(i + 1) * 1000},1000](${(i + 1) * 1000},1000,0)$text" }.joinToString("\n")
        val krc = texts.mapIndexed { i, text -> "[${(i + 1) * 1000},1000]<0,1000,0>$text" }.joinToString("\n")
        val ttml = "<tt><body><div>" + texts.mapIndexed { i, text -> "<p begin=\"${i + 1}s\" end=\"${i + 2}s\">$text</p>" }.joinToString("") + "</div></body></tt>"
        val track = Track(id = "test", title = "", artist = "", artistId = "", album = "", albumId = "", sourceUrl = "", imgUrl = "")
        fun results() = listOf(LyricParser.parse(lrc, "", ""), LyricParser.parseQrc(qrc, ""),
            LyricParser.parse("", "", yrc), LyricParser.parseKrc(krc, ""), TtmlLyricsParser.parse(ttml, track))
        LyricExclusionRules.updateEnabled(true)
        try {
            LyricExclusionRules.updateDefaultRuleEnabled(NEIGHBOR_ID, true)
            results().forEach { assertEquals(listOf("前方正常", "后方正常"), it.lines.map { line -> line.text }) }
            LyricExclusionRules.updateDefaultRuleEnabled(NEIGHBOR_ID, false)
            results().forEach { assertEquals(listOf("前方正常", "这句需要排除", "后方正常"), it.lines.map { line -> line.text }) }
            LyricExclusionRules.updateEnabled(false)
            results().forEach { assertTrue(it.lines.any { line -> line.text == "这句需要排除" }) }
        } finally {
            LyricExclusionRules.updateEnabled(enabled)
            LyricExclusionRules.updateDefaultRuleEnabled(NEIGHBOR_ID, ruleEnabled)
        }
    }

    @Test fun adjacentRuleDoesNotExpandFromOneSideOrRemoveEndpoints() {
        val enabled = LyricExclusionRules.enabled
        val ruleEnabled = LyricExclusionRules.isDefaultRuleEnabled(NEIGHBOR_ID)
        LyricExclusionRules.updateEnabled(true)
        LyricExclusionRules.updateDefaultRuleEnabled(NEIGHBOR_ID, true)
        try {
            assertEquals(listOf(false, true, true, true, false),
                LyricExclusionRules.includeExcludedNeighbors(listOf(false, true, false, true, false)))
            assertEquals(listOf(true, false, false, true),
                LyricExclusionRules.includeExcludedNeighbors(listOf(true, false, false, true)))
            assertEquals(listOf(false, true), LyricExclusionRules.includeExcludedNeighbors(listOf(false, true)))
            assertEquals(listOf(true, false), LyricExclusionRules.includeExcludedNeighbors(listOf(true, false)))
        } finally {
            LyricExclusionRules.updateEnabled(enabled)
            LyricExclusionRules.updateDefaultRuleEnabled(NEIGHBOR_ID, ruleEnabled)
        }
    }

    private companion object { const val NEIGHBOR_ID = "default-special-excluded-neighbors" }
}
