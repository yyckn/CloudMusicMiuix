package com.hypermusic.miuix.data.download

import android.content.*
import android.graphics.Bitmap
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.semantics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.hypermusic.miuix.data.lyrics.*
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.service.MediaPlaybackService
import com.hypermusic.miuix.ui.theme.ThemeSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class OfflineMusicDeviceTest {
    @Test fun downloadedAudioAndRawLyricsSurviveClearingImageCache() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val id = UUID.randomUUID().toString()
        val track = Track("offline-device-$id", "离线测试歌曲", "测试歌手", "1", "离线专辑", "2",
            source = "qqmusic", sourceUrl = "https://offline.invalid/song", imgUrl = "https://offline.invalid/cover", duration = 4_000, isVip = true)
        val folder = context.filesDir.resolve("downloads").apply { mkdirs() }
        val audio = folder.resolve("$id.wav")
        val count = 44100 * 4
        val bytes = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + count * 2).put("WAVEfmt ".toByteArray()).putInt(16)
        bytes.putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(count * 2)
        audio.writeBytes(bytes.array())
        val entry = DownloadEntry(id, track, 0, null, state = "complete", path = audio.absolutePath,
            bytes = audio.length(), quality = "无损", format = "wav", bitrate = 705600)
        val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val old = com.google.gson.JsonParser.parseString(prefs.getString("entries", "[]")).asJsonArray
        val legacy = Gson().toJsonTree(entry).asJsonObject
        val legacyTrack = legacy.getAsJsonObject("track")
        listOf("platformData", "bodianFreeSign", "kugouHash", "kugouAlbumAudioId", "qqMediaMid", "artistNames", "artistIds", "localDownloadId").forEach { legacyTrack.remove(it) }
        old.add(legacy)
        prefs.edit().putString("entries", old.toString()).commit()
        ThemeSettings.init(context)
        LyricMatches.init(context)
        LyricExclusionRules.init(context)
        OfflineLibrary.init(context)
        val original = "[00:01.00]作词：测试\n[00:02.00]离线歌词第一行\n[00:03.00]离线歌词第二行"
        val raw = RawLyricDocument("lrc", original, "[00:02.00]Offline translation")
        val coverBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val cover = ByteArrayOutputStream().also { coverBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        coverBitmap.recycle()
        var service: MediaPlaybackService? = null
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as MediaPlaybackService.LocalBinder).getService(); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null }
        }
        var bound = false
        try {
            assertTrue(OfflineLibrary.save(entry, cover, LyricsRepository.LyricsLoadResult(raw.parse(track), "QQ音乐", false,
                rawDocument = raw, matchedTrack = track)).isEmpty())
            Downloads.init(context)
            val restored = Downloads.entries.first { it.id == id }
            assertEquals("", restored.track.platform.bodianFreeSign)
            assertEquals("", restored.track.platform.kugouHash)
            val local = OfflineLibrary.localTrack(restored)
            val originalAudio = audio.readBytes()
            val rawBeforeRepair = context.filesDir.resolve("downloads/assets/$id/lyrics.json").readText()
            Downloads.update(id) { it.copy(track = it.track.copy(duration = 0), bitrate = 0, format = "") }
            context.filesDir.resolve("downloads/assets/$id/thumbnail.jpg").delete()
            context.filesDir.resolve("downloads/assets/$id/track.json").delete()
            OfflineLibrary.upgradeLegacy(Downloads.entries.first { it.id == id }, allowNetwork = false)
            val repaired = Downloads.entries.first { it.id == id }
            assertEquals("wav", repaired.format)
            assertTrue(repaired.bitrate > 0)
            assertTrue(repaired.track.duration >= 3900)
            assertFalse(OfflineLibrary.needsRepair(repaired))
            assertEquals(rawBeforeRepair, context.filesDir.resolve("downloads/assets/$id/lyrics.json").readText())
            assertArrayEquals("Repair must not rewrite or redownload audio", originalAudio, audio.readBytes())
            assertNotNull(OfflineLibrary.cover(local))
            assertNotNull(OfflineLibrary.cover(local, true))
            com.hypermusic.miuix.data.cache.ImageCaches.clearMemory()
            com.hypermusic.miuix.data.cache.ImageCaches.Kind.entries.forEach {
                com.hypermusic.miuix.data.cache.ImageCaches.get(context, it).diskCache?.clear()
            }
            assertTrue(audio.isFile)
            assertNotNull(OfflineLibrary.cover(local))
            val start = android.os.SystemClock.elapsedRealtime()
            val lyrics = LyricsRepository.load(local).getOrThrow()
            assertTrue("Downloaded lyrics must not wait on any provider", android.os.SystemClock.elapsedRealtime() - start < 1000)
            assertEquals(original, lyrics.rawDocument!!.original)
            assertTrue(lyrics.lyrics.lines.any { it.text == "离线歌词第一行" })
            val shareFile = prepareAudioShareFile(audio, context.cacheDir.resolve("shared_audio"), exportedSongName(track, "wav"))
            val shared = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile, shareFile.name)
            assertEquals("离线测试歌曲 - 测试歌手.wav", shared.lastPathSegment)
            context.contentResolver.query(shared, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
                assertTrue(it.moveToFirst()); assertEquals("离线测试歌曲 - 测试歌手.wav", it.getString(0))
            }
            bound = context.bindService(Intent(context, MediaPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { service!!.playTrackWithUrl(local, audio.toURI().toString(), "download:$id") }
            Thread.sleep(1800)
            instrumentation.runOnMainSync {
                assertTrue("Actual service must decode the downloaded file", service!!.getDuration() >= 3900)
                assertTrue("Local playback must advance", service!!.getCurrentPosition() > 0)
                service!!.clearPlayback()
            }
            val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
            instrumentation.runOnMainSync { activity.setContent {
                top.yukonga.miuix.kmp.theme.MiuixTheme {
                    top.yukonga.miuix.kmp.material.SoftGlassTheme(true, false) {
                        top.yukonga.miuix.kmp.basic.Scaffold { _ ->
                            SharedTransitionLayout {
                                AnimatedContent(Unit, label = "localMusicTest") { _ ->
                                    com.hypermusic.miuix.ui.pages.PlaylistDetailPage(
                                        playlistDetail = com.hypermusic.miuix.data.model.PlaylistDetail(
                                            com.hypermusic.miuix.data.model.Playlist("local", "本地音乐", "", ""), listOf(local)),
                                        isLoading = false, error = null, currentTrack = local, isLocalMusic = true,
                                        onShowDownloads = { Downloads.showSheet = true }, onBackClick = {}, onTrackClick = {},
                                        onPlayAllClick = {}, onShuffleClick = {}, onCheckTrackLiked = { Result.failure(Exception("离线")) },
                                        onSetTrackLiked = { _, _ -> }, sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent)
                                }
                            }
                            com.hypermusic.miuix.ui.pages.DownloadSheet()
                        }
                    }
                }
            } }
            Thread.sleep(700)
            fun nodes(): List<SemanticsNode> {
                val result = mutableListOf<SemanticsNode>()
                fun read(node: SemanticsNode) { result += node; node.children.forEach(::read) }
                fun visit(view: android.view.View) {
                    view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" }?.let { read((it.invoke(view) as SemanticsOwner).rootSemanticsNode) }
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                }
                visit(activity.window.decorView)
                return result
            }
            fun clickDescription(description: String) = instrumentation.runOnMainSync {
                var node: SemanticsNode? = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true }
                while (node != null && node.config.getOrNull(SemanticsActions.OnClick) == null) node = node.parent
                assertTrue(requireNotNull(node).config[SemanticsActions.OnClick].action!!.invoke())
            }
            val screenshots = java.io.File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { java.io.File(it) }
                ?: context.filesDir, "offline-${System.currentTimeMillis()}").apply { mkdirs() }
            fun screenshot(name: String) {
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                screenshots.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            screenshot("local-music")
            clickDescription("下载任务")
            Thread.sleep(700)
            instrumentation.runOnMainSync { assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "历史下载" } == true }) }
            screenshot("download-tasks")
            instrumentation.runOnMainSync { Downloads.showSheet = false }
            Thread.sleep(600)
            clickDescription("更多")
            Thread.sleep(700)
            instrumentation.runOnMainSync {
                val texts = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                assertTrue(texts.containsAll(listOf("分享文件", "导出文件", "删除文件")))
                assertFalse(texts.contains("下载此歌曲"))
            }
            screenshot("local-file-actions")
            instrumentation.runOnMainSync { activity.finish() }
            Downloads.remove(id)
            assertFalse(audio.exists())
            assertNull(OfflineLibrary.find(local))
            assertNull(OfflineLibrary.cover(entry))
        } finally {
            instrumentation.runOnMainSync { service?.clearPlayback() }
            if (bound) context.unbindService(connection)
            context.stopService(Intent(context, MediaPlaybackService::class.java))
            Downloads.remove(id)
            audio.delete()
        }
    }
}
