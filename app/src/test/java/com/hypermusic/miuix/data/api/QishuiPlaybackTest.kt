package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class QishuiPlaybackTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject

    @Test fun selectsHighestPermittedQualityAndPrefersFull() {
        val track = json("""{"player_info":{"full":{"video_model_info":{"video_model":{"video_list":[{"main_url":"https://a.douyinvod.com/low","video_meta":{"quality":"medium"}},{"main_url":"https://a.douyinvod.com/high","video_meta":{"quality":"higher"}},{"main_url":"https://a.douyinvod.com/lossless","video_meta":{"quality":"lossless"}}]}}},"preview":{"video_model_info":{"video_model":{"video_list":[{"main_url":"https://a.douyinvod.com/preview"}]}}}}}""")
        val audio = QishuiPlayback.resolve(track, QishuiPlayback.Quality.HIGH) { error("unexpected request") }
        assertEquals(QishuiPlayback.Quality.HIGH, audio.quality)
        assertTrue(audio.url.endsWith("/high"))
        assertFalse(audio.preview)
        assertEquals("高品质", audio.qualityLabel())
        assertEquals("未知音质", audio.copy(qualityKnown = false).qualityLabel())
    }

    @Test fun skipsEncryptedAndMarksPreview() {
        val track = json("""{"player_info":{"full":{"video_model_info":{"video_model":{"video_list":[{"main_url":"https://a.douyinvod.com/full","Encrypt":1}]}}},"preview":{"duration":{"start_ms":1500},"video_model_info":{"video_model":{"video_list":[{"main_url":"https://a.douyinvod.com/sample"}]}}}}}""")
        val audio = QishuiPlayback.resolve(track, QishuiPlayback.Quality.STANDARD) { error("unexpected request") }
        assertTrue(audio.preview)
        assertEquals(1500L, audio.clipStartMs)
    }

    @Test fun rejectsUntrustedPlayerInfoAndMedia() {
        assertTrue(runCatching { QishuiPlayback.trustedUrl("https://douyin.com.evil.test/audio") }.isFailure)
        assertTrue(runCatching { QishuiPlayback.trustedUrl("http://douyin.com/audio") }.isFailure)
        val track = json("""{"player_info":{"full":{"video_model_info":{"url_player_info":"https://evil.test/player"}}}}""")
        assertTrue(runCatching { QishuiPlayback.resolve(track, QishuiPlayback.Quality.HIGH) { error("must not fetch") } }.isFailure)
    }

    @Test fun readsExternalPlayerInformation() {
        val track = json("""{"player_info":{"full":{"video_model_info":{"url_player_info":"https://api.douyin.com/player"}}}}""")
        val audio = QishuiPlayback.resolve(track, QishuiPlayback.Quality.LOSSLESS) {
            json("""{"Result":{"Data":{"PlayInfoList":[{"MainPlayUrl":"https://cdn.douyinvod.com/audio","Quality":"lossless"}]}}}""")
        }
        assertEquals(QishuiPlayback.Quality.LOSSLESS, audio.quality)
    }

    @Test fun stringFalseEncryptionFlagsDoNotHidePlainAudio() {
        val track = json("""{"player_info":{"full":{"video_model_info":{"video_model":{"video_list":[{"main_url":"https://a.douyinvod.com/audio","Encrypt":"0","encrypt_info":{"encrypt":"false","spade_a":""}}]}}}}}""")
        assertFalse(QishuiPlayback.resolve(track, QishuiPlayback.Quality.STANDARD) { error("unexpected request") }.preview)
    }
}
