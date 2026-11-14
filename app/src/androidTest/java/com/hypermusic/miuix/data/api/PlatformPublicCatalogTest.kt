package com.hypermusic.miuix.data.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import com.hypermusic.miuix.data.model.SearchCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only network checks; no account or changes to a user's library are required. */
@RunWith(AndroidJUnit4::class)
class PlatformPublicCatalogTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun kugouTrackWithOnlyAudioIdCanPlay() = runBlocking {
        val client = KugouClient(context)
        val auth = KugouAuth(context, client)
        val tracks = KugouCatalog(client).searchSongs("晴天", limit = 10).items
        val audio = tracks.firstNotNullOfOrNull { track ->
            if (track.kugouAlbumAudioId.isBlank()) return@firstNotNullOfOrNull null
            runCatching {
                val withoutHash = track.copy(kugouHash = "")
                assertEquals(track.kugouHash.uppercase(), client.trackHash(withoutHash))
                KugouPlayback(client, auth).resolve(withoutHash, KugouPlayback.Quality.STANDARD)
            }.getOrNull()
        } ?: error("未找到可验证的公开歌曲")
        assertPlayerAdvances(audio.url)
    }

    private fun assertPlayerAdvances(url: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var player: androidx.media3.exoplayer.ExoPlayer? = null
        try {
            instrumentation.runOnMainSync {
                player = androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                    prepare()
                    play()
                }
            }
            val deadline = System.currentTimeMillis() + 20_000
            var position = 0L
            var failure: androidx.media3.common.PlaybackException? = null
            while (position < 500 && failure == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
                instrumentation.runOnMainSync { position = player!!.currentPosition; failure = player!!.playerError }
            }
            assertTrue("播放未前进：${failure?.errorCodeName ?: "超时"}", position >= 500)
        } finally { instrumentation.runOnMainSync { player?.release() } }
    }

    @Test fun bodianPublicSearchProducesUsableTracks() = runBlocking {
        val page = BodianCatalog(BodianClient(context)).searchSongs("晴天", limit = 10)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.items.all { it.source == "bodian" && it.id.startsWith("bodian_") && it.title.isNotBlank() })
    }

    @Test fun kugouPublicSearchProducesUsableTracks() = runBlocking {
        val page = KugouCatalog(KugouClient(context)).searchSongs("晴天", limit = 10)
        assertTrue(page.items.isNotEmpty())
        assertTrue(page.items.all { it.source == "kugou" && it.title.isNotBlank() })
    }

    @Test fun qishuiAlbumSearchAndDetailsAgree() = runBlocking {
        val page = QishuiShareAlbum.search("周杰伦", 0, 10)
        val album = page.items.first { it.id.toLongOrNull() != null }
        val detail = QishuiShareAlbum.load(album.id.toLong())
        assertEquals(album.id.toLong(), detail.first.id)
        assertTrue(detail.first.name.isNotBlank())
    }

    @Test fun publicArtistsAndPlaylistsLoad() = runBlocking {
        val bodian = BodianCatalog(BodianClient(context))
        assertTrue(BodianCatalog.parseArtist(bodian.artistDetail("1627")).name.isNotBlank())
        val kugou = KugouCatalog(KugouClient(context))
        assertEquals(3520L, kugou.artist("3520").id)
        assertTrue(kugou.playlist("collection_3_1002717860_518_0", limit = 10).info.size() > 0)
        val qishui = QishuiArtistWorks.artist("6681166129722824706")
        assertTrue(qishui.name.isNotBlank())
    }

    @Test fun qrLoginCanBeCreatedAndCancelledWithoutAnAccount() = runBlocking {
        val bodian = BodianAuth(context, BodianClient(context))
        val b = bodian.createQr()
        assertTrue(b.url.startsWith("https://"))
        bodian.cancel(b)
        val kugou = KugouAuth(context, KugouClient(context))
        val k = kugou.createQr()
        assertTrue(k.url.startsWith("https://"))
        kugou.cancel(k)
        val qishui = QishuiAuth(context, QishuiClient(context))
        val q = qishui.createQr()
        assertTrue(q.image.size > 8)
        qishui.cancel(q)
    }

    @Test fun publicAlbumDetailsContainTracks() = runBlocking {
        val bodian = BodianCatalog(BodianClient(context))
        val album = BodianCatalog.parseAlbum(bodian.albumDetail("520748"))
        assertEquals(520748L, album.id)
        assertTrue(bodian.albumSongs("520748", limit = 10).items.isNotEmpty())
        val kugou = KugouCatalog(KugouClient(context)).album(958909L)
        assertEquals(958909L, kugou.first.id)
        assertTrue(kugou.second.isNotEmpty())
    }

    @Test fun qishuiSearchAndTrackDetailUseTheSameId() = runBlocking {
        val catalog = QishuiCatalog(QishuiClient(context))
        val page = catalog.searchSongs("晴天", limit = 10)
        assertTrue(page.items.isNotEmpty())
        val id = page.items.first().id.removePrefix("qishui_")
        val detail = catalog.songDetail(id)
        assertEquals(id, detail.getAsJsonObject("base_info").get("id").asString)
    }

    @Test fun publicArtistTracksAndAlbumsLoad() = runBlocking {
        val bodian = BodianCatalog(BodianClient(context))
        assertTrue(bodian.artistSongs("1627", limit = 50).items.isNotEmpty())
        assertTrue(bodian.artistAlbums("1627", limit = 50).items.isNotEmpty())
        val kugou = KugouCatalog(KugouClient(context))
        assertTrue(kugou.artistSongs("3520", limit = 50).isNotEmpty())
        assertTrue(kugou.artistAlbums("3520", limit = 50).isNotEmpty())
        assertTrue(QishuiArtistWorks.songs("6681166129722824706", "").items.isNotEmpty())
        val albums = QishuiArtistWorks.albums("7209157886709073952", "").items
        assertTrue(albums.isNotEmpty() && albums.all { it.id > 0 })
    }

    @Test fun publicCategorySearchesReturnMappedResults() = runBlocking {
        val bodian = BodianCatalog(BodianClient(context))
        for (category in listOf(SearchCategory.PLAYLIST, SearchCategory.ALBUM, SearchCategory.ARTIST)) {
            assertTrue("波点分类 $category 无结果", bodian.searchEntities("周杰伦", category, limit = 10).items.isNotEmpty())
        }
        val kugou = KugouCatalog(KugouClient(context))
        for ((type, category) in listOf("special" to SearchCategory.PLAYLIST, "album" to SearchCategory.ALBUM, "author" to SearchCategory.ARTIST)) {
            val results = kugou.searchEntities(type, "周杰伦", limit = 10).items.mapNotNull { KugouCatalog.parseSearchEntity(it, category) }
            assertTrue("酷狗分类 $category 无结果", results.isNotEmpty())
        }
        val qishui = QishuiCatalog(QishuiClient(context))
        assertTrue(qishui.searchPlaylists("周杰伦", limit = 10).items.mapNotNull(QishuiCatalog::playlistEntity).isNotEmpty())
        assertTrue(qishui.searchArtists("周杰伦", limit = 10).items.mapNotNull(QishuiCatalog::artistEntity).isNotEmpty())
        assertTrue(QishuiShareAlbum.search("周杰伦", 0, 10).items.isNotEmpty())
    }

    @Test fun qishuiSearchNextPageDoesNotRepeatTheFirstPage() = runBlocking {
        val catalog = QishuiCatalog(QishuiClient(context))
        val first = catalog.searchSongs("周杰伦", limit = 10)
        assertTrue(first.items.isNotEmpty() && first.hasMore)
        val second = catalog.searchSongs("周杰伦", offset = first.nextOffset, limit = 10)
        assertTrue(second.items.isNotEmpty())
        assertTrue("分页结果重复", first.items.map { it.id }.toSet().intersect(second.items.map { it.id }.toSet()).isEmpty())
    }

    @Test fun qishuiPublicTrackResolvesAndReturnsAudioBytes() = runBlocking {
        val client = QishuiClient(context)
        val catalog = QishuiCatalog(client)
        val track = catalog.searchSongs("晴天", limit = 10).items.first()
        val detail = catalog.songDetail(track.id.removePrefix("qishui_"))
        val audio = QishuiPlayback(client).resolve(detail, QishuiPlayback.Quality.STANDARD)
        val request = okhttp3.Request.Builder().url(audio.url).header("Range", "bytes=0-1023").build()
        okhttp3.OkHttpClient().newCall(request).execute().use { response ->
            assertTrue("HTTP ${response.code}", response.isSuccessful)
            val source = requireNotNull(response.body).source()
            assertTrue(source.request(16))
            assertTrue(com.hypermusic.miuix.data.download.AudioFileFormat.detect(source.readByteArray(16)) != null)
        }
    }

    @Test fun kugouPublicTrackResolvesAndReturnsAudioBytes() = runBlocking {
        val client = KugouClient(context)
        val auth = KugouAuth(context, client)
        val tracks = KugouCatalog(client).searchSongs("晴天", limit = 10).items
        val audio = tracks.firstNotNullOfOrNull { track ->
            runCatching { KugouPlayback(client, auth).resolve(track, KugouPlayback.Quality.STANDARD) }.getOrNull()
        } ?: error("测试歌曲均未返回可播放音频")
        val request = okhttp3.Request.Builder().url(audio.url).header("Range", "bytes=0-1023").build()
        okhttp3.OkHttpClient().newCall(request).execute().use { response ->
            assertTrue("HTTP ${response.code}", response.isSuccessful)
            val source = requireNotNull(response.body).source()
            assertTrue(source.request(16))
            assertTrue(com.hypermusic.miuix.data.download.AudioFileFormat.detect(source.readByteArray(16)) != null)
        }
    }

    @Test fun bodianPublicTrackResolvesAndReturnsAudioBytes() = runBlocking {
        val client = BodianClient(context)
        val tracks = BodianCatalog(client).searchSongs("晴天", limit = 10).items
        val audio = tracks.firstNotNullOfOrNull { track ->
            runCatching { BodianPlayback(client).resolve(track, BodianPlayback.Quality.STANDARD) }.getOrNull()
        } ?: error("测试歌曲均未返回可播放音频")
        val request = okhttp3.Request.Builder().url(audio.url).header("Range", "bytes=0-1023").build()
        okhttp3.OkHttpClient().newCall(request).execute().use { response ->
            assertTrue("HTTP ${response.code}", response.isSuccessful)
            val source = requireNotNull(response.body).source()
            assertTrue(source.request(16))
            assertTrue(com.hypermusic.miuix.data.download.AudioFileFormat.detect(source.readByteArray(16)) != null)
        }
    }

    @Test fun publicQishuiTrackPlaysThroughExoPlayer() = runBlocking {
        val client = QishuiClient(context)
        val catalog = QishuiCatalog(client)
        val track = catalog.searchSongs("晴天", limit = 10).items.first()
        val audio = QishuiPlayback(client).resolve(catalog.songDetail(track.id.removePrefix("qishui_")), QishuiPlayback.Quality.STANDARD)
        assertPlayerAdvances(audio.url)
    }

    @Test fun publicKugouTrackPlaysThroughExoPlayer() = runBlocking {
        val client = KugouClient(context)
        val auth = KugouAuth(context, client)
        val tracks = KugouCatalog(client).searchSongs("晴天", limit = 10).items
        val audio = tracks.firstNotNullOfOrNull { runCatching { KugouPlayback(client, auth).resolve(it, KugouPlayback.Quality.STANDARD) }.getOrNull() }
            ?: error("测试歌曲均未返回可播放音频")
        assertPlayerAdvances(audio.url)
    }

    @Test fun publicBodianTrackPlaysThroughExoPlayer() = runBlocking {
        val client = BodianClient(context)
        val tracks = BodianCatalog(client).searchSongs("晴天", limit = 10).items
        val audio = tracks.firstNotNullOfOrNull { runCatching { BodianPlayback(client).resolve(it, BodianPlayback.Quality.STANDARD) }.getOrNull() }
            ?: error("测试歌曲均未返回可播放音频")
        assertPlayerAdvances(audio.url)
    }

    @Test fun bodianNativeLyricsCanBeLoaded() = runBlocking {
        com.hypermusic.miuix.data.lyrics.BodianLyricsApi.init(context)
        val songs = BodianCatalog(BodianClient(context)).searchSongs("晴天", limit = 5).items
        val lyrics = songs.firstNotNullOfOrNull { com.hypermusic.miuix.data.lyrics.BodianLyricsApi.fetch(it) }
        assertTrue(lyrics != null && lyrics.lyrics.lines.isNotEmpty())
    }

    @Test fun kugouNativeLyricsCanBeLoaded() = runBlocking {
        val songs = KugouCatalog(KugouClient(context)).searchSongs("晴天", limit = 5).items
        val lyrics = songs.firstNotNullOfOrNull { com.hypermusic.miuix.data.lyrics.KugouLyricsApi.fetch(it) }
        assertTrue(lyrics != null && lyrics.lyrics.lines.isNotEmpty())
    }

    @Test fun kugouNativeLyricsWorkWithAudioIdAndNoSearchMetadata() = runBlocking {
        com.hypermusic.miuix.data.lyrics.KugouLyricsApi.init(context)
        val track = KugouCatalog.parseTrack(com.google.gson.JsonParser.parseString("""{"album_audio_id":32029511}""").asJsonObject)
        val lyrics = com.hypermusic.miuix.data.lyrics.KugouLyricsApi.fetch(track)
        assertTrue(lyrics != null && lyrics.lyrics.lines.isNotEmpty())
    }

    @Test fun qishuiNativeLyricsCanBeLoaded() = runBlocking {
        com.hypermusic.miuix.data.lyrics.QishuiLyricsApi.init(context)
        val songs = QishuiCatalog(QishuiClient(context)).searchSongs("晴天", limit = 5).items
        val lyrics = songs.firstNotNullOfOrNull { com.hypermusic.miuix.data.lyrics.QishuiLyricsApi.fetch(it) }
        assertTrue(lyrics != null && lyrics.lyrics.lines.isNotEmpty())
    }

    @Test fun smartLyricsUseTheNativeSourceWhenUpgradeIsDisabled() = runBlocking {
        val settings = com.hypermusic.miuix.ui.theme.ThemeSettings
        settings.init(context)
        com.hypermusic.miuix.data.lyrics.LyricMatches.init(context)
        com.hypermusic.miuix.data.lyrics.LyricExclusionRules.init(context)
        com.hypermusic.miuix.data.lyrics.BodianLyricsApi.init(context)
        com.hypermusic.miuix.data.lyrics.QishuiLyricsApi.init(context)
        val oldPreference = settings.lyricSourcePreferenceIndex
        val oldUpgrade = settings.autoUpgradeLyrics
        try {
            settings.updateLyricSourcePreferenceIndex(0)
            settings.updateAutoUpgradeLyrics(false)
            val samples = listOf(
                BodianCatalog(BodianClient(context)).searchSongs("晴天", limit = 5).items to "波点音乐",
                KugouCatalog(KugouClient(context)).searchSongs("晴天", limit = 5).items to "酷狗音乐",
                QishuiCatalog(QishuiClient(context)).searchSongs("晴天", limit = 5).items to "汽水音乐"
            )
            for ((tracks, expected) in samples) {
                val native = tracks.firstNotNullOfOrNull { track ->
                    val result = com.hypermusic.miuix.data.lyrics.LyricsRepository.load(track).getOrNull()
                    result?.takeIf { it.source == expected && it.lyrics.lines.isNotEmpty() }
                }
                assertTrue("未从 $expected 获取原平台歌词", native != null)
            }
        } finally {
            settings.updateLyricSourcePreferenceIndex(oldPreference)
            settings.updateAutoUpgradeLyrics(oldUpgrade)
        }
    }

    @Test fun chosenKugouLyricsTakePriorityForBodianTracks() = runBlocking {
        val settings = com.hypermusic.miuix.ui.theme.ThemeSettings
        settings.init(context)
        com.hypermusic.miuix.data.lyrics.LyricMatches.init(context)
        com.hypermusic.miuix.data.lyrics.LyricExclusionRules.init(context)
        com.hypermusic.miuix.data.lyrics.BodianLyricsApi.init(context)
        val oldPreference = settings.lyricSourcePreferenceIndex
        val oldUpgrade = settings.autoUpgradeLyrics
        try {
            settings.updateLyricSourcePreferenceIndex(3)
            settings.updateAutoUpgradeLyrics(false)
            val tracks = BodianCatalog(BodianClient(context)).searchSongs("晴天 周杰伦", limit = 5).items
            val result = tracks.firstNotNullOfOrNull { track ->
                com.hypermusic.miuix.data.lyrics.LyricsRepository.load(track).getOrNull()
                    ?.takeIf { it.source == "酷狗音乐" && it.lyrics.lines.isNotEmpty() }
            }
            assertTrue("未能优先使用酷狗音乐歌词", result != null)
        } finally {
            settings.updateLyricSourcePreferenceIndex(oldPreference)
            settings.updateAutoUpgradeLyrics(oldUpgrade)
        }
    }

    @Test fun qishuiGuestDownloadProducesARecognizableAudioFile() = runBlocking {
        assertTrue("仅可在隔离测试包运行", context.packageName.endsWith(".platformtest"))
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        org.junit.Assume.assumeTrue("WorkManager requires a validated network",
            manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        val client = QishuiClient(context)
        val catalog = QishuiCatalog(client)
        val track = catalog.searchSongs("晴天", limit = 10).items.firstOrNull { candidate ->
            runCatching {
                val detail = catalog.songDetail(candidate.id.removePrefix("qishui_"))
                !QishuiPlayback(client).resolve(detail, QishuiPlayback.Quality.STANDARD).preview
            }.getOrDefault(false)
        } ?: error("未找到可供测试的完整公开歌曲")
        assertGuestDownload(track, 3)
    }

    @Test fun kugouGuestDownloadProducesARecognizableAudioFile() = runBlocking {
        val client = KugouClient(context)
        val auth = KugouAuth(context, client)
        val track = KugouCatalog(client).searchSongs("晴天", limit = 10).items.firstOrNull { candidate ->
            runCatching { !KugouPlayback(client, auth).resolve(candidate, KugouPlayback.Quality.STANDARD).preview }.getOrDefault(false)
        } ?: error("未找到可供测试的完整公开歌曲")
        assertGuestDownload(track, 3)
    }

    @Test fun bodianGuestDownloadProducesARecognizableAudioFile() = runBlocking {
        val client = BodianClient(context)
        val track = BodianCatalog(client).searchSongs("晴天", limit = 10).items.firstOrNull { candidate ->
            runCatching { BodianPlayback(client).resolve(candidate, BodianPlayback.Quality.STANDARD).preview == null }.getOrDefault(false)
        } ?: error("未找到可供测试的完整公开歌曲")
        assertGuestDownload(track, 2)
    }

    private suspend fun assertGuestDownload(track: com.hypermusic.miuix.data.model.Track, qualityIndex: Int) {
        assertTrue("仅可在隔离测试包运行", context.packageName.endsWith(".platformtest"))
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        org.junit.Assume.assumeTrue("WorkManager requires a validated network",
            manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        val prefs = context.getSharedPreferences("downloads", android.content.Context.MODE_PRIVATE)
        val previousWarning = prefs.getBoolean("warn_mobile", true)
        val qualityKey = "quality_${track.source}"
        val oldQuality = prefs.getInt(qualityKey, -1)
        val downloads = com.hypermusic.miuix.data.download.Downloads
        downloads.init(context)
        val before = downloads.entries.map { it.id }.toSet()
        var id: String? = null
        try {
            prefs.edit().putBoolean("warn_mobile", false).putInt(qualityKey, qualityIndex).commit()
            downloads.enqueue(context, track, null)
            id = downloads.entries.first { it.id !in before }.id
            val deadline = System.currentTimeMillis() + 60_000
            var entry = downloads.entries.first { it.id == id }
            while (entry.state !in setOf("complete", "failed") && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(250)
                entry = downloads.entries.first { it.id == id }
            }
            assertEquals("下载状态：${entry.state}；${entry.message}", "complete", entry.state)
            val file = java.io.File(entry.path)
            assertTrue(file.isFile && file.length() > 1024)
            val header = file.inputStream().use { it.readNBytes(16) }
            assertTrue(com.hypermusic.miuix.data.download.AudioFileFormat.detect(header) != null)
            if (entry.message.isBlank()) {
                org.jaudiotagger.tag.TagOptionSingleton.getInstance().isAndroid = true
                val tag = org.jaudiotagger.audio.AudioFileIO.read(file).tag
                assertTrue("未提示缺失，但歌词为空", !tag.getFirst(org.jaudiotagger.tag.FieldKey.LYRICS).isNullOrBlank())
                assertTrue("未提示缺失，但封面为空", tag.artworkList.isNotEmpty())
            }
        } finally {
            id?.let(downloads::remove)
            prefs.edit().putBoolean("warn_mobile", previousWarning).putInt(qualityKey, oldQuality).commit()
        }
    }
}
