package com.hypermusic.miuix.data.download

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadSharingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun sharedFileHasRealSongNameAndDoesNotRenameTheDownload() {
        val source = temporary.newFile("123456789.flac").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val cache = temporary.newFolder("share")
        val shared = prepareAudioShareFile(source, cache, "歌名 - 歌手.flac")
        assertEquals("歌名 - 歌手.flac", shared.name)
        assertArrayEquals(source.readBytes(), shared.readBytes())
        assertEquals(shared, prepareAudioShareFile(source, cache, "歌名 - 歌手.flac"))
        assertTrue(shared.delete())
        assertTrue(source.isFile)
        assertArrayEquals(byteArrayOf(1, 2, 3), source.readBytes())
    }

    @Test fun songsWithSameDisplayNameKeepTheirOwnContents() {
        val first = temporary.newFile("first.mp3").apply { writeText("first recording") }
        val second = temporary.newFile("second.mp3").apply { writeText("second recording") }
        val cache = temporary.newFolder("share")
        val a = prepareAudioShareFile(first, cache, "同名歌曲 - 歌手.mp3")
        val b = prepareAudioShareFile(second, cache, "同名歌曲 - 歌手.mp3")
        assertNotEquals(a, b)
        assertEquals("first recording", a.readText())
        assertEquals("second recording", b.readText())
    }
}
