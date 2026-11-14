package com.hypermusic.miuix.data.lyrics

import com.google.gson.JsonParser
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QishuiLyricsTest {
    @Test fun selectsChineseTranslationInsteadOfAnUnrelatedLanguage() {
        val track = Track(id = "qishui_123", title = "", artist = "", artistId = "", album = "", albumId = "", source = "qishui", sourceUrl = "", imgUrl = "")
        fun parse(translations: String) = QishuiLyricsApi.parse(JsonParser.parseString("""{"lyric_entity":{"type":"lrc","content":"[00:01.00]original"},"lang_translations":$translations}""").asJsonObject, track)!!
        assertEquals("中文", parse("""{"en":{"content":"[00:01.00]English"},"ZH_HANS_CN":{"content":"[00:01.00]中文"}}""").lyrics.lines.first().translation)
        assertTrue(parse("""{"en":{"content":"[00:01.00]English"}}""").lyrics.lines.first().translation.isNullOrEmpty())
    }
    @Test fun parsesWordLyricsAndTimestampedTranslation() {
        val info = JsonParser.parseString("""{"lyric_entity":{"type":"krc","content":"[1000,1000]<0,500,0>你<500,500,0>好\n[3000,1000]<0,1000,0>世界\n[5000,1000]<0,1000,0>再次\n[7000,1000]<0,1000,0>相见"},"lang_translations":{"zh-CN":{"type":"KRC","content":"[1000,1000]<0,1000,0>你好呀"}}}""").asJsonObject
        val track = Track(id = "qishui_123", title = "", artist = "", artistId = "", album = "", albumId = "", source = "qishui", sourceUrl = "", imgUrl = "")
        val result = QishuiLyricsApi.parse(info, track)!!
        assertTrue(result.hasWordLines)
        assertEquals("你好", result.lyrics.lines.first().text)
        assertEquals("你好呀", result.lyrics.lines.first().translation)
        assertEquals(1000L, result.lyrics.lines.first().words!!.first().startAt)
    }
}
