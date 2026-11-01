package com.hypermusic.miuix.data.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in: verifies a real account and restores the original favorite state. */
@RunWith(AndroidJUnit4::class)
class QqLiveFavoritesTest {
    @Test
    fun addAndRemoveRestoresFavorites() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("qqLiveMutation") == "true")
        QqMusicApi.init(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("QQ Music account is required", QqMusicApi.isLoggedIn())
        val existing = QqMusicApi.getPlaylistDetail("qqdir:201").getOrThrow().tracks.map { it.id }.toSet()
        val candidate = QqMusicApi.searchSongs("jay").getOrThrow().firstOrNull { it.qqSongId > 0 && it.id !in existing }
        assertNotNull("No suitable test song", candidate)
        val song = candidate!!
        println("LIVE_TEST_ID=${song.id}")
        var attempted = false
        try {
            attempted = true
            QqMusicApi.setLiked(song, true).getOrThrow()
            delay(1500)
            assertTrue("Song was not added", QqMusicApi.isLiked(song).getOrThrow())
        } finally {
            if (attempted) {
                val nowLiked = QqMusicApi.isLiked(song).getOrThrow()
                if (nowLiked) QqMusicApi.setLiked(song, false).getOrThrow()
                delay(1500)
                assertFalse("Test song was not removed", QqMusicApi.isLiked(song).getOrThrow())
            }
        }
    }
}
