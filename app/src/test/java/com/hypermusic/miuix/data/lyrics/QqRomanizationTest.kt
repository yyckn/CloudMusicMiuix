package com.hypermusic.miuix.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class QqRomanizationTest {
    private val qrc = """
        <QrcInfos><Lyric_1 LyricContent="[12514,2208]Dear (12514,319)my (12833,162)crazy (12995,314)soulmate(13309,1413)
        [14867,1259]Your (14867,380)story(15247,879)
        [16421,2639]결국에 (16421,542)세상이 (16963,547)우릴 (17510,420)포기할 (17930,609)때까지(18539,521)
        [19060,3088]이대로만 (19060,1945)우리 (21005,412)둘만(21417,731)"/></QrcInfos>
    """.trimIndent()

    @Test fun romanizationMatchesQrcAndTranslationByTime() {
        val result = LyricParser.parseQrc(qrc, "[00:16.42]直到世界放弃我们", phonetic = "[00:16.42]gyeolgug-e sesang-i")
        val line = result.lines.first { it.startAt == 16421L }
        assertEquals("gyeolgug-e sesang-i", line.phonetic)
        assertEquals("直到世界放弃我们", line.translation)
    }

    @Test fun romanizationSupportsQrcXmlAndWordTimings() {
        val roma = """<QrcInfos><Lyric_1 LyricContent="[16421,2639]gyeolgug-e (16421,542)sesang-i(16963,547)"/></QrcInfos>"""
        val result = LyricParser.parseQrc(qrc, "", phonetic = roma)
        assertEquals("gyeolgug-e sesang-i", result.lines.first { it.startAt == 16421L }.phonetic)
    }

    @Test fun noRomanizationLeavesLyricsUnchanged() {
        assertEquals("", LyricParser.parseQrc(qrc, "").lines.first { it.startAt == 16421L }.phonetic)
    }
}
