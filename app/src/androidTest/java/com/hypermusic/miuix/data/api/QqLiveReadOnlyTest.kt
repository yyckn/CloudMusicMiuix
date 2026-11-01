package com.hypermusic.miuix.data.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Live API smoke test. It never writes to the account and is disabled by default. */
@RunWith(AndroidJUnit4::class)
class QqLiveReadOnlyTest {
    @Test fun searchRecommendationsPlaylistsAndPlayback() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("qqLiveRead") == "true")
        QqMusicApi.init(InstrumentationRegistry.getInstrumentation().targetContext)
        assertTrue("QQ account required", QqMusicApi.isLoggedIn())
        assertTrue("QQ profile has no name", QqMusicApi.refreshProfile().getOrThrow().isNotBlank())
        val songs = QqMusicApi.searchSongs("jay").getOrThrow()
        assertTrue("search returned no songs", songs.isNotEmpty())
        assertTrue(songs.all { it.source == "qqmusic" && it.id.isNotBlank() })
        assertTrue("no playable URL returned", songs.take(12).any {
            QqMusicApi.getSongAudio(it.id, it.qqMediaMid, 3).getOrNull()?.url?.startsWith("https://") == true
        })
        val recommendations = QqMusicApi.getRecommendPlaylists().getOrThrow()
        assertTrue("no recommended playlists", recommendations.isNotEmpty())
        val detail = QqMusicApi.getPlaylistDetail(recommendations.first().id).getOrThrow()
        assertTrue("recommended playlist has no songs", detail.tracks.isNotEmpty())
        assertTrue(detail.tracks.all { it.source == "qqmusic" })
        val myPlaylists = QqMusicApi.getUserPlaylists().getOrThrow()
        assertTrue("no user playlists", myPlaylists.isNotEmpty())
        val favorites = QqMusicApi.getPlaylistDetail("qqdir:201").getOrThrow()
        assertNotNull(favorites.tracks)
        val withArtist = songs.first { it.artistId.startsWith("qqartist_") }
        val artistMid = withArtist.artistId.removePrefix("qqartist_")
        val artist = QqMusicApi.getArtistInfo(artistMid).getOrThrow()
        assertTrue("artist has no name", artist.name.isNotBlank())
        assertTrue("artist has no portrait", artist.picUrl.startsWith("https://"))
        assertTrue("artist description is empty", QqMusicApi.getArtistInfo("0025NhlN2yWrP4").getOrThrow().briefDesc.isNotBlank())
        val firstArtistPage = QqMusicApi.getArtistSongs(artistMid).getOrThrow()
        assertTrue(firstArtistPage.isNotEmpty())
        if (firstArtistPage.size == 30) {
            val nextArtistPage = QqMusicApi.getArtistSongs(artistMid, offset = 30).getOrThrow()
            assertTrue("artist pagination repeated the first page", nextArtistPage.none { next -> firstArtistPage.any { it.id == next.id } })
        }
        assertTrue("artist albums are empty", QqMusicApi.getArtistAlbums(artistMid).getOrThrow().isNotEmpty())
        val firstAlbums = QqMusicApi.getArtistAlbums("0025NhlN2yWrP4", limit = 20).getOrThrow()
        val nextAlbums = QqMusicApi.getArtistAlbums("0025NhlN2yWrP4", offset = 20, limit = 20).getOrThrow()
        assertEquals(20, firstAlbums.size)
        assertTrue("artist album pagination failed", nextAlbums.isNotEmpty() && nextAlbums.none { next -> firstAlbums.any { it.id == next.id } })
        val withAlbum = songs.first { (it.albumId.toLongOrNull() ?: 0L) > 0 }
        assertTrue(QqMusicApi.getAlbumSongs(withAlbum.albumId.toLong()).getOrThrow().isNotEmpty())
        val withId = songs.first { it.qqSongId > 0 }
        assertNotNull(QqMusicApi.getComments(withId.qqSongId).getOrThrow())
    }
}
