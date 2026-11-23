package com.hypermusic.miuix.history

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.history.*
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.ui.pages.ListeningHistoryPage
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import top.yukonga.miuix.kmp.material.SoftGlassTheme
import top.yukonga.miuix.kmp.theme.*
import java.time.*

class ListeningHistoryUiDeviceTest {
    @Test fun dateGroupsAndAllTabsRenderInLightAndDarkGlass() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        NeteaseApi.init(context)
        ListeningHistory.init(context)
        val ids = listOf(ListeningHistory.newId(), ListeningHistory.newId())
        val track = Track("history-ui-test", "听歌记录界面测试", "测试歌手", "", "测试专辑", "", sourceUrl = "", imgUrl = "")
        val today = LocalDate.now()
        ids.forEachIndexed { index, id ->
            val date = today.minusDays(index.toLong())
            ListeningHistory.save(ListeningRecord(id, track.copy(id = track.id + index), NeteaseApi.getCurrentUserId()?.toString().orEmpty(), date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                90_000, 180_000, false, false, false, null, mapOf(date.toString() to 90_000L)))
        }
        ListeningHistory.awaitWrites()
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ComponentActivity
        val store = ViewModelStore()
        val dark = mutableStateOf(false)
        val glass = mutableStateOf(true)
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
        fun has(text: String): Boolean = nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
        fun select(text: String) {
            instrumentation.runOnMainSync {
                var node: SemanticsNode? = nodes().first { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
                while (node != null && node.config.getOrNull(SemanticsActions.OnClick) == null) node = node.parent
                assertTrue(requireNotNull(node).config[SemanticsActions.OnClick].action!!.invoke())
            }
            Thread.sleep(700)
        }
        try {
            instrumentation.runOnMainSync {
                val model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(activity.application))[MusicPlayerViewModel::class.java]
                activity.setContent {
                    MiuixTheme(controller = ThemeController(if (dark.value) ColorSchemeMode.Dark else ColorSchemeMode.Light)) {
                        SoftGlassTheme(glass.value, false) { ListeningHistoryPage(model, {}, {}) }
                    }
                }
            }
            Thread.sleep(1500)
            instrumentation.runOnMainSync { assertTrue(has("今天")); assertTrue(has("昨天")) }
            for (isGlass in listOf(true, false)) for (isDark in listOf(false, true)) {
                instrumentation.runOnMainSync { dark.value = isDark; glass.value = isGlass }
                select("周"); instrumentation.runOnMainSync { assertTrue(has("本周收听时长")) }
                select("月"); instrumentation.runOnMainSync { assertTrue(has("本月收听时长")) }
                select("年"); instrumentation.runOnMainSync { assertTrue(has("全年收听时长")) }
                select("日"); instrumentation.runOnMainSync { assertTrue(has("今天")) }
            }
            // A screenshot stays in isolated test data for optional visual inspection.
            val bitmap = android.graphics.Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, android.graphics.Bitmap.Config.ARGB_8888)
            val copied = java.util.concurrent.CountDownLatch(1)
            var copyResult = -1
            android.view.PixelCopy.request(activity.window, bitmap, { copyResult = it; copied.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
            assertTrue(copied.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(android.view.PixelCopy.SUCCESS, copyResult)
            context.cacheDir.resolve("listening-history-ui.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        } finally {
            instrumentation.runOnMainSync { store.clear(); activity.finish() }
            ListeningHistory.deleteForTest(ids)
        }
    }
}
