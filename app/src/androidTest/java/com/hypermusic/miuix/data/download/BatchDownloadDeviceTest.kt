package com.hypermusic.miuix.data.download

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.hypermusic.miuix.data.lyrics.*
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.ui.theme.ThemeSettings
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BatchDownloadDeviceTest {
    @Test fun triStateSelectionAndManualConflictsPreserveExistingDownloads() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ComponentActivity
        val prefix = "batch-${UUID.randomUUID()}"
        val tracks = (1..3).map { Track("$prefix-$it", "批量测试$it", "歌手", "", "专辑", "", source = "offline-test", sourceUrl = "", imgUrl = "", duration = 1000) }
        val folder = context.filesDir.resolve("downloads").apply { mkdirs() }
        val fixtures = tracks.take(2).map { track ->
            val id = UUID.randomUUID().toString()
            val file = folder.resolve("$id.mp3").apply { writeBytes(ByteArray(1024)) }
            DownloadEntry(id, track, 0, null, state = "complete", bytes = 1024, total = 1024,
                path = file.absolutePath, quality = "无损", bitrate = 128000, format = "mp3")
        }
        val prefs = context.getSharedPreferences("downloads", android.content.Context.MODE_PRIVATE)
        val oldWarning = prefs.getBoolean("warn_mobile", true)
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
        fun click(node: SemanticsNode) {
            var target: SemanticsNode? = node
            while (target != null && target.config.getOrNull(SemanticsActions.OnClick) == null) target = target.parent
            assertTrue(requireNotNull(target).config[SemanticsActions.OnClick].action!!.invoke())
        }
        fun clickText(text: String) { instrumentation.runOnMainSync {
            click(nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t ->
                if (text.startsWith("开始下载")) t.text.startsWith("$text（") else t.text == text } == true })
        }; Thread.sleep(500) }
        fun hasText(text: String): Boolean {
            var result = false
            instrumentation.runOnMainSync { result = nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t ->
                if (text.startsWith("开始下载")) t.text.startsWith("$text（") else t.text == text } == true } }
            return result
        }
        // Toolbar semantics are not guaranteed to precede the list after recomposition.
        fun selectAllCheckbox() = nodes().filter { it.config.getOrNull(SemanticsProperties.ToggleableState) != null }
            .minBy { it.boundsInWindow.top }
        try {
            instrumentation.runOnMainSync { activity.setContent {
                top.yukonga.miuix.kmp.theme.MiuixTheme {
                    top.yukonga.miuix.kmp.material.SoftGlassTheme(true, false) {
                        top.yukonga.miuix.kmp.basic.Scaffold { _ ->
                            com.hypermusic.miuix.ui.components.BatchDownloadSheets()
                            com.hypermusic.miuix.ui.components.DownloadDeduplicationDialogs()
                        }
                    }
                }
            }; BatchDownloads.open(tracks) }
            Thread.sleep(1200)
            assertTrue(hasText("开始下载3首歌曲"))
            instrumentation.runOnMainSync { click(selectAllCheckbox()) }
            Thread.sleep(300)
            assertTrue(hasText("开始下载0首歌曲"))
            instrumentation.runOnMainSync { click(selectAllCheckbox()) }
            Thread.sleep(300)
            assertTrue(hasText("开始下载3首歌曲"))
            clickText("批量测试1")
            assertTrue(hasText("开始下载2首歌曲"))
            instrumentation.runOnMainSync {
                assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.ToggleableState) == androidx.compose.ui.state.ToggleableState.Indeterminate })
                click(selectAllCheckbox())
            }
            Thread.sleep(300)
            clickText("开始下载3首歌曲")
            assertEquals(2, BatchDownloads.conflicts?.proposed?.size)
            assertTrue(Downloads.entries.any { it.track.id == tracks[2].id })
            clickText("手动选择是否下载")
            assertTrue(hasText("解决冲突歌曲"))
            assertFalse(hasText("重新下载2首歌曲"))
            clickText("已有版本：")
            assertTrue(hasText("重新下载1首歌曲"))
            clickText("重新下载1首歌曲")
            assertNull(BatchDownloads.conflicts)
            assertTrue(fixtures.all { java.io.File(it.path).isFile })
            assertTrue(Downloads.entries.any { it.track.id == tracks[1].id && it.id !in fixtures.map { f -> f.id } })
            assertFalse(Downloads.entries.any { it.track.id == tracks[0].id && it.id !in fixtures.map { f -> f.id } })
            instrumentation.runOnMainSync { kotlinx.coroutines.runBlocking { BatchDownloads.start(context, listOf(tracks.first())) } }
            Thread.sleep(500)
            assertNotNull(Downloads.duplicateConfirmation)
            clickText("取消")
            assertNull(Downloads.duplicateConfirmation)
            assertTrue(fixtures.all { java.io.File(it.path).isFile })
            val beforeCancel = Downloads.entries.map { it.id }.toSet()
            instrumentation.runOnMainSync { kotlinx.coroutines.runBlocking { BatchDownloads.start(context, tracks.take(2)) } }
            Thread.sleep(500)
            clickText("全部取消下载")
            assertNull(BatchDownloads.conflicts)
            assertEquals(beforeCancel, Downloads.entries.map { it.id }.toSet())
            instrumentation.runOnMainSync { kotlinx.coroutines.runBlocking { BatchDownloads.start(context, tracks.take(2)) } }
            Thread.sleep(500)
            clickText("全部重新下载")
            assertNull(BatchDownloads.conflicts)
            assertTrue(tracks.take(2).all { track -> Downloads.entries.any { it.track.id == track.id && it.id !in fixtures.map { f -> f.id } } })
            assertTrue(fixtures.all { java.io.File(it.path).isFile })
        } finally {
            instrumentation.runOnMainSync {
                BatchDownloads.closeSelection()
                BatchDownloads.conflicts?.let { BatchDownloads.resolve(it.id, emptySet()) }
                Downloads.cancelDuplicateDownload()
                Downloads.entries.filter { it.track.id.startsWith(prefix) }.forEach { Downloads.remove(it.id) }
                Downloads.showSheet = false
                activity.finish()
            }
            prefs.edit().putBoolean("warn_mobile", oldWarning).commit()
        }
    }
}
