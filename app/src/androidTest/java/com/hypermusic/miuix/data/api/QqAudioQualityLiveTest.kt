package com.hypermusic.miuix.data.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.data.model.QqAudioFormats
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QqAudioQualityLiveTest {
    @Test fun returnedAudioMatchesItsActualFilenameAndQualityCeiling() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("qqLiveRead") == "true")
        QqMusicApi.init(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("QQ account required", QqMusicApi.isLoggedIn())
        val songs = QqMusicApi.searchSongs("周杰伦").getOrThrow()
        val song = songs.firstOrNull { QqMusicApi.getSongAudio(it.id, it.qqMediaMid, 0).isSuccess }
        assertNotNull("No playable track found for the test account", song)
        val track = requireNotNull(song)
        val outputs = (0..4).mapNotNull { index ->
            QqMusicApi.getSongAudio(track.id, track.qqMediaMid, index).getOrNull()?.let { index to it }
        }
        assertTrue(outputs.isNotEmpty())
        outputs.forEach { (index, audio) ->
            val format = QqAudioFormats.match(QqAudioFormats.candidates(index), audio.url)
            assertNotNull("Returned file exceeds selected quality or has unknown format", format)
            assertEquals(format!!.extension, audio.extension)
            assertEquals(format.quality, audio.quality)
        }
        outputs.forEach { (_, a) ->
            val probe = Request.Builder().url(a.url).header("Referer", "https://y.qq.com/").header("Range", "bytes=0-31").build()
            runCatching { OkHttpClient().newCall(probe).execute().use { println("QUALITY_PROBE ${a.quality} ${it.code} ${it.request.url.host}") } }
        }
        val audio = outputs.first().second
        val req = Request.Builder().url(audio.url).header("Referer", "https://y.qq.com/").header("Range", "bytes=0-31").build()
        OkHttpClient().newCall(req).execute().use { response ->
            assertTrue("Audio HTTP status: ${response.code}", response.isSuccessful)
            val source = requireNotNull(response.body).source()
            source.request(12)
            val bytes = source.readByteArray(12)
            when (audio.extension) {
                "flac" -> assertEquals("fLaC", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
                "m4a" -> assertEquals("ftyp", String(bytes.copyOfRange(4, 8), Charsets.US_ASCII))
                "mp3" -> assertTrue("Invalid MP3 signature", String(bytes.copyOfRange(0, 3), Charsets.US_ASCII) == "ID3" || (bytes[0].toInt() and 0xff == 0xff))
            }
        }
    }
}
