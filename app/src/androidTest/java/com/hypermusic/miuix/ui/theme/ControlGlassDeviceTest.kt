package com.hypermusic.miuix.ui.theme

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.material.LocalSoftGlassTabsEnabled
import top.yukonga.miuix.kmp.material.SoftGlassTheme
import top.yukonga.miuix.kmp.overlay.*
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.window.*

/** 隔离安装后运行：验证真实 GPU 着色器、跨窗口/Overlay 弹层和深浅色，不改动正式版数据。 */
@RunWith(AndroidJUnit4::class)
class ControlGlassDeviceTest {
    @Test fun popupMaterialsRenderInBothStylesAndThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue("Run using the isolated .glasstest applicationId", context.packageName.endsWith(".glasstest"))
        val automation = instrumentation.uiAutomation
        automation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        val soft = mutableStateOf(true)
        val dark = mutableStateOf(false)
        val kind = mutableIntStateOf(0)
        val show = mutableStateOf(false)
        val selected = mutableIntStateOf(1)
        var barBehavior: ScrollBehavior? = null
        var composed = 0
        var choices = 0
        instrumentation.runOnMainSync { activity.setContent {
            MiuixTheme(controller = ThemeController(if (dark.value) ColorSchemeMode.Dark else ColorSchemeMode.Light)) {
                SoftGlassTheme(soft.value, dark.value) {
                    val behavior = MiuixScrollBehavior()
                    SideEffect { barBehavior = behavior }
                    SideEffect { composed++ }
                    Scaffold(topBar = {
                        TopAppBar(title = "柔光玻璃测试", largeTitle = "柔光玻璃测试",
                            scrollBehavior = behavior,
                            navigationIcon = { IconButton(onClick = {}) { Text("返回") } },
                            actions = { IconButton(onClick = {}) { Text("按钮") } })
                    }) { padding ->
                        // 不给正文铺底色：验证 Scaffold 外的底色也被正确录入采样，卡片间隙不能变黑。
                        Column(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxWidth().height(160.dp)) {
                                repeat(8) { index ->
                                    Row(Modifier.fillMaxWidth().weight(1f)) {
                                        listOf(Color(0xFF7CA5CD), Color(0xFFB49BCC), Color(0xFF80B7A6)).forEachIndexed { column, _ ->
                                            val colors = listOf(Color(0xFF7CA5CD), Color(0xFFB49BCC), Color(0xFF80B7A6))
                                            Box(Modifier.weight(1f).fillMaxHeight().background(colors[(index + column) % 3]))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(padding.calculateTopPadding()))
                            TabRowWithContour(tabs = listOf("页面一", "页面二"), selectedTabIndex = selected.intValue,
                                onTabSelected = { selected.intValue = it }, modifier = Modifier.padding(12.dp))
                            CompositionLocalProvider(LocalSoftGlassTabsEnabled provides false) {
                                TabRow(tabs = listOf("保留单曲", "保留歌手"), selectedTabIndex = 0, onTabSelected = {}, modifier = Modifier.padding(12.dp))
                            }
                            val entry = DropdownEntry((1..6).map { DropdownItem("菜单选项 $it", onClick = { choices++ }) })
                            key(kind.intValue) {
                                Box(Modifier.padding(16.dp)) {
                                    Text("弹层锚点")
                                    when (kind.intValue) {
                                        0 -> WindowListPopup(show = show.value, onDismissRequest = { show.value = false }) { MenuRows(entry) }
                                        1 -> WindowCascadingListPopup(show = show.value, entries = listOf(entry), onDismissRequest = { show.value = false })
                                        2 -> OverlayListPopup(show = show.value, onDismissRequest = { show.value = false }) { MenuRows(entry) }
                                        3 -> OverlayCascadingListPopup(show = show.value, entries = listOf(entry), onDismissRequest = { show.value = false })
                                        4 -> OverlayDialog(show = show.value, title = "柔光对话框", onDismissRequest = { show.value = false }) { Text("对话框内容") }
                                        5 -> OverlayBottomSheet(show = show.value, title = "柔光抽屉", onDismissRequest = { show.value = false }) { Text("抽屉内容", Modifier.padding(24.dp)) }
                                        6 -> WindowIconDropdownMenu(entry = entry) { Text("菜单") }
                                        7 -> WindowDropdownPreference(entry = entry, title = "打开偏好菜单")
                                    }
                                }
                            }
                            repeat(5) { index ->
                                Card(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Box(Modifier.size(48.dp).background(if (index % 2 == 0) Color(0xFFB49BCC) else Color(0xFF80B7A6)))
                                        Text("背景卡片 $index：文字、封面和控件", Modifier.padding(12.dp))
                                        Switch(checked = index % 2 == 0, onCheckedChange = {})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } }
        Thread.sleep(700)
        var rendered = 0
        val screenshotRun = "run-${System.currentTimeMillis()}"
        fun snapshot(name: String) {
            val bitmap = requireNotNull(automation.takeScreenshot())
            val destination = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.filesDir, screenshotRun)
            destination.mkdirs()
            File(destination, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        for (isDark in listOf(false, true)) for (isSoft in listOf(true, false)) {
            instrumentation.runOnMainSync { dark.value = isDark; soft.value = isSoft }
            instrumentation.runOnMainSync { barBehavior?.state?.let { it.contentOffset = -300f; it.heightOffset = it.heightOffsetLimit } }
            Thread.sleep(350)
            if (isSoft) snapshot("topbar-progressive-$isDark")
            for (mode in 0..7) {
                android.util.Log.i("GlassTest", "render dark=$isDark soft=$isSoft popup=$mode")
                instrumentation.runOnMainSync { kind.intValue = mode; show.value = true }
                if (mode >= 6) { Thread.sleep(300); clickSemanticText(if (mode == 6) "菜单" else "打开偏好菜单") }
                if (mode == 0 && isSoft) {
                    Thread.sleep(45)
                    snapshot("menu-$isDark-opening-1")
                    Thread.sleep(80)
                    snapshot("menu-$isDark-opening-2")
                }
                Thread.sleep(1000)
                if (mode == 0 && isSoft) snapshot("menu-$isDark-open")
                if (mode == 4) {
                    val snapshot = requireNotNull(automation.takeScreenshot())
                    val destination = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.filesDir, screenshotRun)
                    destination.mkdirs()
                    File(destination, "glass-$isSoft-$isDark.png").outputStream().use {
                        snapshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                if (mode >= 6) clickSemanticText("菜单选项 2")
                instrumentation.runOnMainSync { show.value = false; assertEquals(1, selected.intValue) }
                if (mode == 0 && isSoft) { Thread.sleep(80); snapshot("menu-$isDark-closing") }
                Thread.sleep(600)
                rendered++
            }
        }
        val previousMonet = ThemeSettings.followMonet
        val previousStyle = ThemeSettings.controlStyleIndex
        val previousHome = ThemeSettings.defaultHomePageIndex
        instrumentation.runOnMainSync {
            assertTrue(composed >= 4); assertEquals(32, rendered); assertTrue(choices >= 8)
            ThemeSettings.updateFollowMonet(true)
            ThemeSettings.updateControlStyle(0)
            activity.setContent {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    SoftGlassTheme(true, false) {
                        Box(Modifier.fillMaxWidth().height(560.dp)) { com.hypermusic.miuix.ui.pages.ThemeSettingsPage(onBackClick = {}) }
                    }
                }
            }
        }
        Thread.sleep(900)
        snapshot("theme-page-initial")
        val decor = activity.window.decorView
        val x = decor.width * .82f
        val pageHeight = (560f * context.resources.displayMetrics.density).coerceAtMost(decor.height.toFloat())
        val y = pageHeight * .8f
        val endY = pageHeight * .28f
        val now = android.os.SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
        for (step in 1..15) {
            Thread.sleep(20)
            instrumentation.sendPointerSync(android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_MOVE, x, y + (endY - y) * step / 15f, 0))
        }
        instrumentation.sendPointerSync(android.view.MotionEvent.obtain(now, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, x, endY, 0))
        Thread.sleep(800)
        snapshot("theme-page-scrolled")
        clickSemanticText("默认首页")
        Thread.sleep(700)
        snapshot("official-preference-open")
        clickSemanticText("收藏页")
        Thread.sleep(700)
        snapshot("official-preference-selected")
        instrumentation.runOnMainSync {
            assertEquals(1, ThemeSettings.defaultHomePageIndex)
            var scrolled = false
            fun read(node: SemanticsNode) {
                node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let { if (it.value() > 0f) scrolled = true }
                node.children.forEach(::read)
            }
            fun visit(view: android.view.View) {
                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" }?.let { read((it.invoke(view) as SemanticsOwner).rootSemanticsNode) }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach(::visit)
            ThemeSettings.updateFollowMonet(previousMonet)
            ThemeSettings.updateControlStyle(previousStyle)
            ThemeSettings.updateDefaultHomePage(previousHome)
            assertTrue("The real theme page must actually scroll beneath its bar", scrolled)
            activity.finish()
        }
    }

    private fun clickSemanticText(text: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var clicked = false
            instrumentation.runOnMainSync {
                val matches = mutableListOf<SemanticsNode>()
                fun read(node: SemanticsNode) {
                    if (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true) matches += node
                    node.children.forEach(::read)
                }
                fun visit(view: android.view.View) {
                    view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" }?.let { read((it.invoke(view) as SemanticsOwner).rootSemanticsNode) }
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                }
                android.view.inspector.WindowInspector.getGlobalWindowViews().forEach(::visit)
                var node = matches.lastOrNull()
                while (node != null && !clicked) {
                    clicked = node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
                    node = node.parent
                }
            }
            if (clicked) return
            Thread.sleep(50)
        }
        error("No clickable text: $text")
    }
}

@Composable
private fun MenuRows(entry: DropdownEntry) {
    ListPopupColumn {
        entry.items.forEachIndexed { index, item ->
            DropdownImpl(item = item, optionSize = entry.items.size, isSelected = false,
                index = index, onSelectedIndexChange = {})
        }
    }
}
