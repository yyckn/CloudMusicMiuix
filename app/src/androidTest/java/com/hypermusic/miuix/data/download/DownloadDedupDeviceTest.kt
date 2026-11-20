package com.hypermusic.miuix.data.download

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.semantics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.hypermusic.miuix.data.lyrics.*
import com.hypermusic.miuix.data.model.*
import com.hypermusic.miuix.ui.pages.PlaylistDetailPage
import com.hypermusic.miuix.ui.theme.ThemeSettings
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.material.SoftGlassTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class DownloadDedupDeviceTest {
    @Test fun duplicateConfirmationAndVersionSelectionPreserveFilesUntilConfirmed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        val songId = "dedup-${UUID.randomUUID()}"
        val track = Track(songId, "去重测试歌曲", "歌手", "", "专辑", "", source = "offline-test", sourceUrl = "", imgUrl = "", duration = 1000)
        val folder = context.filesDir.resolve("downloads").apply { mkdirs() }
        fun version(quality: String, bytes: Int, song: Track = track): DownloadEntry {
            val id = UUID.randomUUID().toString()
            val file = folder.resolve("$id.mp3").apply { writeBytes(ByteArray(bytes)) }
            return DownloadEntry(id, song, 0, null, state = "complete", bytes = bytes.toLong(), path = file.absolutePath, quality = quality, bitrate = 128000, format = "mp3")
        }
        val low = version("标准品质（128kbps）", 1024)
        val high = version("Hi-Res 无损", 2048)
        val other = version("无损", 2048, track.copy(source = "offline-test-other", title = "其他平台同名歌曲"))
        val fixtures = listOf(low, high, other)
        val prefs = context.getSharedPreferences("downloads", android.content.Context.MODE_PRIVATE)
        val oldWarn = prefs.getBoolean("warn_mobile", true)
        val json = com.google.gson.JsonParser.parseString(prefs.getString("entries", "[]")).asJsonArray
        fixtures.forEach { json.add(Gson().toJsonTree(it)) }
        prefs.edit().putString("entries", json.toString()).putBoolean("warn_mobile", false).commit()
        ThemeSettings.init(context); LyricMatches.init(context); LyricExclusionRules.init(context); OfflineLibrary.init(context)
        val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val cover = java.io.ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        image.recycle()
        fixtures.forEach {
            val raw = RawLyricDocument("lrc", "[00:00.00]测试歌词")
            OfflineLibrary.save(it, cover, LyricsRepository.LyricsLoadResult(raw.parse(it.track), "测试", false, rawDocument = raw))
        }
        Downloads.init(context)
        val played = AtomicReference<String?>(null)
        val output = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.filesDir,
            "dedup-${System.currentTimeMillis()}").apply { mkdirs() }
        fun screenshot(name: String) {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            output.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun allNodes(): List<SemanticsNode> {
            val result = mutableListOf<SemanticsNode>()
            fun read(node: SemanticsNode) { result += node; node.children.forEach(::read) }
            fun visit(view: android.view.View) {
                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" }?.let { read((it.invoke(view) as SemanticsOwner).rootSemanticsNode) }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            visit(activity.window.decorView)
            return result
        }
        fun click(text: String, description: Boolean = false) = instrumentation.runOnMainSync {
            val matches = allNodes().filter { node -> if (description) node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true
                else node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
            var node = if (description) matches.firstOrNull() else matches.lastOrNull()
            while (node != null && node.config.getOrNull(SemanticsActions.OnClick) == null) node = node.parent
            assertTrue("Click $text", requireNotNull(node).config[SemanticsActions.OnClick].action!!.invoke())
        }
        fun back() { instrumentation.uiAutomation.executeShellCommand("input keyevent 4").use { java.io.FileInputStream(it.fileDescriptor).readBytes() }; Thread.sleep(500) }
        try {
            instrumentation.runOnMainSync { activity.setContent {
                MiuixTheme { SoftGlassTheme(true, false) { Scaffold { _ ->
                    SharedTransitionLayout { AnimatedContent(Unit, label = "duplicateTest") { _ ->
                        val groups = groupLocalDownloads(OfflineLibrary.available().filter { it.track.id == songId })
                        val tracks = groups.map { OfflineLibrary.localTrack(it.first()) }
                        PlaylistDetailPage(PlaylistDetail(Playlist("local", "本地音乐", "", ""), tracks), false, null, null,
                            isLocalMusic = true, trackQuality = { t -> groups.first { sameDownloadSong(it.first().track, t) }.first().qualityLabel() },
                            trackVersionCount = { t -> groups.first { sameDownloadSong(it.first().track, t) }.size },
                            resolveTrackForMore = { t, open -> Downloads.requestLocalVersion(t) { open(OfflineLibrary.localTrack(it)) } },
                            onBackClick = {}, onTrackClick = { t -> Downloads.requestLocalVersion(t) { played.set(it.id) } },
                            onPlayAllClick = {}, onShuffleClick = {}, onCheckTrackLiked = { Result.success(false) }, onSetTrackLiked = { _, _ -> },
                            sharedTransitionScope = this@SharedTransitionLayout, animatedVisibilityScope = this@AnimatedContent)
                    } }
                    com.hypermusic.miuix.ui.pages.DownloadSheet()
                } } }
            } }
            Thread.sleep(650)
            assertEquals(2, groupLocalDownloads(OfflineLibrary.available().filter { it.track.id == songId }).size)
            screenshot("grouped-list")
            val before = Downloads.entries.size
            instrumentation.runOnMainSync { kotlinx.coroutines.runBlocking { Downloads.enqueue(context, track, null) } }
            Thread.sleep(600)
            assertNotNull(Downloads.duplicateConfirmation)
            assertEquals(before, Downloads.entries.size)
            instrumentation.runOnMainSync {
                val buttonText = allNodes().last { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "重新下载" } == true }
                assertTrue("Confirmation text must be fully visible", buttonText.boundsInWindow.height > 20f)
            }
            screenshot("duplicate-download")
            click("取消")
            Thread.sleep(400)
            assertNull(Downloads.duplicateConfirmation)
            assertTrue(File(low.path).exists()); assertTrue(File(high.path).exists())
            click("去重测试歌曲")
            Thread.sleep(500)
            assertNotNull(Downloads.localVersionRequest)
            assertNull(played.get())
            instrumentation.runOnMainSync { Downloads.dismissLocalVersion() }
            Thread.sleep(500)
            assertTrue(File(low.path).exists()); assertTrue(File(high.path).exists())
            click("更多", description = true)
            Thread.sleep(600)
            screenshot("select-version")
            click("Hi-Res 无损")
            assertTrue(File(low.path).exists())
            click("确定")
            Thread.sleep(500)
            assertNull(Downloads.localVersionRequest)
            assertFalse(File(low.path).exists())
            assertTrue(File(high.path).exists()); assertTrue(File(other.path).exists())
            assertEquals(1, OfflineLibrary.versions(track).size)
            back()
            // Re-download failure must preserve the retained complete file.
            instrumentation.runOnMainSync { Downloads.showSheet = false; kotlinx.coroutines.runBlocking { Downloads.enqueue(context, track, null) } }
            Thread.sleep(300)
            val proposed = requireNotNull(Downloads.duplicateConfirmation).proposed
            instrumentation.runOnMainSync { Downloads.confirmDuplicateDownload() }
            val deadline = System.currentTimeMillis() + 15000
            while (System.currentTimeMillis() < deadline && Downloads.entries.find { it.id == proposed.id }?.state != "failed") Thread.sleep(100)
            assertEquals("failed", Downloads.entries.find { it.id == proposed.id }?.state)
            assertTrue(File(high.path).exists())
            val replacement = folder.resolve("${proposed.id}.mp3").apply { writeBytes(ByteArray(4096)) }
            assertTrue(Downloads.finish(proposed.id, replacement.absolutePath, replacement.length(), ""))
            assertFalse(File(high.path).exists()); assertTrue(File(other.path).exists())
            assertEquals(1, OfflineLibrary.versions(track).size)
            assertTrue(replacement.exists())
        } finally {
            instrumentation.runOnMainSync { Downloads.cancelDuplicateDownload(); Downloads.dismissLocalVersion(); Downloads.showSheet = false; activity.finish() }
            Downloads.entries.filter { it.track.id == songId }.forEach { Downloads.remove(it.id) }
            fixtures.forEach { File(it.path).delete(); OfflineLibrary.remove(it.id) }
            prefs.edit().putBoolean("warn_mobile", oldWarn).apply()
        }
    }
}
