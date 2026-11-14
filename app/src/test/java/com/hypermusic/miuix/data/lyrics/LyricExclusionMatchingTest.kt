package com.hypermusic.miuix.data.lyrics

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class LyricExclusionMatchingTest {
    private fun excluded(text: String) = LyricExclusionRules.shouldExclude(text, 3000, "", emptyList(), "")

    @Test fun historicalDefaultIdStillControlsTheSameRule() {
        val enabled = LyricExclusionRules.enabled
        val ruleEnabled = LyricExclusionRules.isDefaultRuleEnabled("default-colon-1")
        LyricExclusionRules.updateEnabled(true)
        try {
            LyricExclusionRules.updateDefaultRuleEnabled("default-colon-1", true)
            assertTrue(excluded("母带：某人"))
            LyricExclusionRules.updateDefaultRuleEnabled("default-colon-1", false)
            assertFalse(excluded("母带：某人"))
            assertTrue(excluded("音乐制作发行某工作室"))
        } finally {
            LyricExclusionRules.updateDefaultRuleEnabled("default-colon-1", ruleEnabled)
            LyricExclusionRules.updateEnabled(enabled)
        }
    }

    @Test fun presetDurationLimitIncludesUpperBound() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            assertTrue(LyricExclusionRules.shouldExclude("工作团队", 1999, "", emptyList(), ""))
            assertFalse(LyricExclusionRules.shouldExclude("工作团队", 2000, "", emptyList(), ""))
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun plainCopyrightAndPrefixCreditsDoNotRequireColon() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            assertTrue(excluded("本作品版权所有，保留一切权利"))
            assertTrue(excluded("音乐制作发行某工作室"))
            assertTrue(excluded("作詞 某人"))
            assertFalse(excluded("我们一起音乐制作发行"))
            assertFalse(excluded("这一句作詞 某人"))
            assertFalse(excluded("作詞某人"))
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun sixMetadataSeparatorRulesAcceptUnknownTextOnEitherSide() {
        assertEquals(listOf("{title} - ", " - {title}", "{auther} - ", " - {auther}", "{album} - ", " - {album}"),
            LyricExclusionRules.defaultTitleRules.map { it.pattern })
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            fun matches(text: String) = LyricExclusionRules.shouldExclude(text, 3000, "晴天", listOf("周杰伦", "另一位歌手"), "叶惠美")
            for (value in listOf("晴天", "周杰伦", "另一位歌手", "叶惠美")) {
                assertTrue(value, matches("$value - 未知信息"))
                assertTrue(value, matches("未知信息 - $value"))
                assertTrue(value, matches("前缀 $value - 未知信息 后缀"))
                assertTrue(value, matches("前缀 未知信息 - $value 后缀"))
            }
            assertFalse(matches("其他歌曲 - 其他信息"))
            assertFalse(matches("晴天依旧灿烂"))
            assertFalse(LyricExclusionRules.shouldExclude("未知 - 任意", 3000, "", emptyList(), ""))
            assertTrue(LyricExclusionRules.shouldExclude("未知 - Love+You", 3000, "Love+You", emptyList(), ""))
            assertFalse(LyricExclusionRules.shouldExclude("未知 - LoveeeeeYou", 3000, "Love+You", emptyList(), ""))
            assertTrue(LyricExclusionRules.shouldExclude("乙 - 未知", 3000, "", listOf("甲 / 乙"), ""))
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun mergedCreditRulesMatchAnywhereAndRequireAColon() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            for (colon in listOf(":", "：", "﹕")) {
                for (credit in listOf("Engineer", "Mixing Engineer", "Recording Engineer", "Studio",
                    "Recording Studio", "Mixing Studio", "by", "Arranged by", "Produced by", "念白",
                    "吴语念白", "彝语念白", "录音师", "人声录音师", "母带工程师", "造型师", "师 Credit")) {
                    assertTrue(credit, excluded("前缀 $credit $colon 某人"))
                    assertTrue(credit, excluded("前缀 $credit$colon 某人"))
                }
            }
            for (text in listOf("正文 Engineer Someone", "正文 Studio Somewhere", "正文 by Someone", "正文念白某人", "我想念老师")) {
                assertFalse(text, excluded(text))
            }
            val patterns = listOf("Engineer\\s*[:：﹕]", "Studio\\s*[:：﹕]", "by\\s*[:：﹕]",
                "念白\\s*[:：﹕]", "师.*\\s*[:：﹕]")
            for (pattern in patterns) {
                val rule = LyricExclusionRules.defaultRules.single { it.pattern == pattern }
                assertEquals(LyricExclusionRules.MatchType.ANYWHERE, rule.effectiveMatchType)
            }
            assertFalse(LyricExclusionRules.defaultColonRules.any {
                it.pattern.startsWith("Mixing Engineer") || it.pattern.startsWith("Recording Studio") ||
                    it.pattern.startsWith("录音师") || it.pattern.startsWith("彝语念白") || it.pattern.startsWith("Arranged by")
            })
            assertFalse(LyricExclusionRules.defaultExtraRules.any { it.pattern.contains("Engineer") || it.pattern.contains("Studio") })
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun titleSeparatorRuleAcceptsArbitraryCreditsAndEscapesSongMetadata() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            fun matches(text: String, title: String) = LyricExclusionRules.shouldExclude(text, 3000, title, emptyList(), "")
            assertTrue(matches("晴天 - 未知人员", "晴天"))
            assertTrue(matches("前缀 晴天 - 其他信息", "晴天"))
            assertTrue(matches("Love+You - Someone", "Love+You"))
            assertFalse(matches("另一首 - 未知人员", "晴天"))
            assertFalse(matches("我在晴天想起你", "晴天"))
            assertFalse(matches("任意歌曲 - 未知人员", ""))
            assertFalse(matches("LoveeeeeYou - Someone", "Love+You"))
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun customMatchingTypesAndLegacyRules() {
        val enabled = LyricExclusionRules.enabled
        val previous = LyricExclusionRules.userRules.toList()
        LyricExclusionRules.updateEnabled(true)
        previous.forEach { LyricExclusionRules.removeUserRule(it.id) }
        try {
            val legacy = Gson().fromJson("""{"id":"legacy","pattern":"测试关键词"}""", LyricExclusionRules.Rule::class.java)
            assertEquals(LyricExclusionRules.MatchType.ANYWHERE, legacy.effectiveMatchType)
            for (type in LyricExclusionRules.MatchType.entries) {
                LyricExclusionRules.addUserRule(legacy.copy(matchType = type))
                assertTrue(excluded("测试关键词"))
                assertEquals(type in listOf(LyricExclusionRules.MatchType.ANYWHERE, LyricExclusionRules.MatchType.START), excluded("测试关键词正文"))
                assertEquals(type in listOf(LyricExclusionRules.MatchType.ANYWHERE, LyricExclusionRules.MatchType.END), excluded("正文测试关键词"))
                assertEquals(type == LyricExclusionRules.MatchType.ANYWHERE, excluded("正文测试关键词正文"))
                LyricExclusionRules.removeUserRule(legacy.id)
            }
            LyricExclusionRules.addUserRule(legacy)
            assertTrue(excluded("正文测试关键词正文"))
        } finally {
            LyricExclusionRules.userRules.toList().forEach { LyricExclusionRules.removeUserRule(it.id) }
            previous.forEach { LyricExclusionRules.addUserRule(it) }
            LyricExclusionRules.updateEnabled(enabled)
        }
    }

    @Test fun creditPrefixesAndNewCopyrightRules() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            assertTrue(excluded("编曲：某人"))
            assertTrue(excluded("作词：某人"))
            assertTrue(excluded("作曲：某人"))
            assertTrue(excluded("和声编写：某人"))
            assertTrue(excluded("和声编写:某人"))
            assertFalse(excluded("这是和声编写：正文"))
            assertTrue(excluded("我想编曲：给你听"))
            assertTrue(excluded("本作品禁止未经授权二创改编，谢谢"))
            assertTrue(excluded("禁止二创改编"))
            assertFalse(excluded("禁止翻唱"))
            assertTrue(excluded("来自酷狗音乐人·星曜计划"))
            assertEquals(listOf("开头"), LyricExclusionRules.tags(LyricExclusionRules.defaultColonRules.first { it.pattern.startsWith("母带") }))
            assertEquals(listOf("特殊规则"), LyricExclusionRules.tags(LyricExclusionRules.defaultRules.first()))
            assertTrue(LyricExclusionRules.tags(LyricExclusionRules.defaultShortDurationRules.first()).single().contains("1999"))
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun expandedCreditRulesSupportThreeColonsAndMatchingPositions() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            val startKeywords = listOf("人声配唱", "录音师", "音频编辑", "封面设计", "OP/SP", "混音师", "母带工程师", "项目统筹", "营销推广", "Bass")
            val flexibleKeywords = listOf("作词", "作曲", "编曲", "改编", "混音", "手风琴", "巴拉莱卡", "弦乐", "小提琴", "器乐监制")
            for (colon in listOf(":", "：", "﹕")) {
                startKeywords.forEach { assertTrue(it, excluded("$it $colon 某人")) }
                flexibleKeywords.forEach {
                    assertTrue(it, excluded("前缀$it$colon 某人"))
                    assertTrue(it, excluded("前缀$it Credit $colon 某人"))
                }
                assertTrue(excluded("项目统筹 $colon 某人"))
                assertTrue(excluded("Produced by $colon 某人"))
                assertFalse(excluded("前缀人声配唱 $colon 正文"))
            }
            assertTrue(excluded("本歌曲著作权利保留，请知悉"))
            assertTrue(excluded("本歌曲著作相关权利保留，请知悉"))
            assertFalse(excluded("本歌曲著作相关说明，请知悉"))
            assertFalse(excluded("没有冒号的器乐监制正文"))
            assertEquals(LyricExclusionRules.defaultRules.size, LyricExclusionRules.defaultRules.map { it.id }.toSet().size)
            assertFalse(LyricExclusionRules.defaultExtraRules.any { it.pattern.startsWith("作词") || it.pattern.startsWith("编曲") })
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun additionalCreditsMergeIntoBroaderRules() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            for (colon in listOf(":", "：", "﹕")) {
                assertTrue(excluded("音乐设计 $colon 某人"))
                assertTrue(excluded("录音棚 Studio $colon 某处"))
                assertTrue(excluded("前缀录音棚 Studio $colon 正文"))
                for (keyword in listOf("键盘", "监制")) {
                    assertTrue(keyword, excluded("$keyword $colon 某人"))
                    assertTrue(keyword, excluded("$keyword Credit $colon 某人"))
                    assertFalse(keyword, excluded("前缀$keyword Credit $colon 正文"))
                }
                for (keyword in listOf("总监", "顾问", "设计", "策划", "词", "曲", "吉他", "统筹", "公司", "鸣谢", "发行")) {
                    assertTrue(keyword, excluded("前缀${keyword} Credit $colon 某人"))
                    assertFalse(keyword, excluded("没有冒号的${keyword}正文"))
                }
                assertTrue(excluded("音乐总监 $colon 某人"))
                assertTrue(excluded("合唱团录音师 $colon 某人"))
                assertTrue(excluded("合唱团监棚 $colon 某人"))
                assertTrue(excluded("前缀录音师 Credit $colon 某人"))
                assertFalse(excluded("前缀合唱团监棚 $colon 正文"))
                assertTrue(excluded("作词 Lyricist $colon 某人"))
            }
            val rules = LyricExclusionRules.defaultColonRules
            assertEquals(1, rules.count { it.pattern == "词.*\\s*[:：﹕]" })
            assertFalse(rules.any { it.pattern.startsWith("作词") || it.pattern.startsWith("编曲") })
            assertFalse(rules.any { it.pattern.startsWith("录音棚") })
            assertEquals(LyricExclusionRules.MatchType.ANYWHERE, rules.single { it.pattern == "录音.*\\s*[:：﹕]" }.effectiveMatchType)
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun expandedProductionCreditsMergeAndRespectTheirPositions() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            for (colon in listOf(":", "：", "﹕")) {
                for (keyword in listOf("和声编写", "文案", "贝斯", "鼓")) {
                    assertTrue(excluded("${keyword} Credit $colon 某人"))
                    assertFalse(excluded("前缀${keyword} Credit $colon 某人"))
                    assertEquals(1, LyricExclusionRules.defaultRules.count { it.pattern == "$keyword.*\\s*[:：﹕]" })
                    assertFalse(LyricExclusionRules.defaultRules.any { it.pattern == "$keyword\\s*[:：﹕]" })
                }
                for (keyword in listOf("Lyricist", "Composer", "音乐营销机构", "声乐编辑", "配唱制作", "版权方")) {
                    assertTrue(keyword, excluded("$keyword $colon 某人"))
                }
                for (keyword in listOf("录音", "编辑")) {
                    assertTrue(excluded("前缀${keyword} Credit $colon 某人"))
                    assertFalse(excluded("没有冒号的${keyword}正文"))
                }
            }
            for (text in listOf("这里有录音棚的名字", "欢迎网易音乐人参与", "获得流量扶持计划")) assertTrue(text, excluded(text))
            assertFalse(excluded("鼓点是我们的语言"))
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun englishProductionPrefixesDoNotRequireColons() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            for (prefix in listOf("mix by", "prod by")) {
                assertTrue(excluded("$prefix Someone"))
                assertTrue(excluded("$prefix：Someone"))
                assertFalse(excluded("正文 $prefix Someone"))
                val rule = LyricExclusionRules.defaultRules.first { it.pattern == prefix }
                assertEquals(listOf("开头"), LyricExclusionRules.tags(rule))
            }
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun repeatedUnknownLabelsWorkAcrossAllTimedFormats() {
        val lrc = (1..2).joinToString("\n") { "[00:0$it.00]神秘嘉宾：正文$it" }
        val qrc = (1..2).joinToString("\n") { "[${it * 1000},1000]神秘嘉宾：(0,100)正文$it(100,900)" }
        val yrc = (1..2).joinToString("\n") { "[${it * 1000},1000](${it * 1000},1000,0)神秘嘉宾：正文$it" }
        val krc = (1..2).joinToString("\n") { "[${it * 1000},1000]<0,1000,0>神秘嘉宾：正文$it" }
        val results = listOf(LyricParser.parse(lrc, "", ""), LyricParser.parseQrc(qrc, ""),
            LyricParser.parse("", "", yrc), LyricParser.parseKrc(krc, ""))
        results.forEach { lyrics ->
            assertEquals(2, lyrics.lines.count { it.isArtistLine && it.text == "神秘嘉宾" })
            assertEquals(2, lyrics.lines.count { !it.isArtistLine && it.text.startsWith("正文") })
        }
        assertFalse(LyricParser.parse("[00:01]神秘嘉宾：下一首", "", "").lines.any { it.isArtistLine })
    }

    @Test fun fixedLabelsAndSingerInitialsPreserveTimedWords() {
        val lyrics = LyricParser.parse("", "", "[1000,1000](1000,100,0)汪：(1100,900,0)你好\n[2000,1000](2000,1000,0)男歌手合\n[3000,1000](3000,1000,0)女歌手合", artistNames = listOf("汪苏泷"))
        assertEquals(3, lyrics.lines.count { it.isArtistLine })
        val body = lyrics.lines.first { it.text == "你好" }
        assertEquals(1100L, body.words!!.first().startAt)
        assertEquals("你好", body.words!!.joinToString("") { it.text })
    }
}
