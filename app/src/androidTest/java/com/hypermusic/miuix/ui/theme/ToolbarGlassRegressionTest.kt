package com.hypermusic.miuix.ui.theme

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.ui.components.SearchPlatformActions
import com.hypermusic.miuix.ui.components.pageScrollTopInset
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.material.SoftGlassTheme
import top.yukonga.miuix.kmp.theme.*

@RunWith(AndroidJUnit4::class)
class ToolbarGlassRegressionTest {
    @Test fun emptyNavigationSlotActionsAndRefreshStayBelowBar() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        val refreshing = mutableStateOf(false)
        val dark = mutableStateOf(false)
        var barBottom = 0f
        val output = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let(::File) ?: context.filesDir, "toolbar-${System.currentTimeMillis()}").apply { mkdirs() }
        fun screenshot(name: String) {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(output, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun nodes(): List<SemanticsNode> {
            val found = mutableListOf<SemanticsNode>()
            fun read(node: SemanticsNode) { found += node; node.children.forEach(::read) }
            fun visit(view: android.view.View) {
                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" }?.let {
                    read((it.invoke(view) as SemanticsOwner).rootSemanticsNode)
                }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            visit(activity.window.decorView)
            return found
        }
        instrumentation.runOnMainSync {
            activity.setContent {
                MiuixTheme(controller = ThemeController(if (dark.value) ColorSchemeMode.Dark else ColorSchemeMode.Light)) {
                    SoftGlassTheme(true, dark.value) {
                        val behavior = MiuixScrollBehavior()
                        Scaffold(topBar = {
                            TopAppBar("推荐", modifier = Modifier.onGloballyPositioned { barBottom = it.boundsInWindow().bottom },
                                scrollBehavior = behavior, actions = { SearchPlatformActions({}) })
                        }) { padding ->
                            PullToRefresh(isRefreshing = refreshing.value, onRefresh = { refreshing.value = true },
                                refreshTexts = listOf("继续下拉以刷新", "松手后刷新", "正在刷新...", "刷新成功"),
                                topAppBarScrollBehavior = behavior,
                                contentPadding = PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding())),
                                modifier = Modifier.padding(top = pageViewportTopInset(padding.calculateTopPadding()))) {
                                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding()))) {
                                    items(25) { Card(Modifier.padding(12.dp).fillMaxWidth()) { Text("内容 $it", Modifier.padding(24.dp)) } }
                                }
                            }
                        }
                    }
                }
            }
        }
        try {
            for (isDark in listOf(false, true)) {
                instrumentation.runOnMainSync { dark.value = isDark }
                Thread.sleep(600)
                var iconBounds = androidx.compose.ui.geometry.Rect.Zero
                instrumentation.runOnMainSync {
                    val buttons = nodes().filter { it.config.getOrNull(SemanticsActions.OnClick) != null && it.boundsInWindow.bottom <= barBottom }
                    assertEquals("Empty navigation slots must not create a third button", 2, buttons.size)
                    assertEquals(buttons[0].boundsInWindow.width, buttons[1].boundsInWindow.width, 1f)
                    assertEquals(buttons[0].boundsInWindow.height, buttons[1].boundsInWindow.height, 1f)
                    val icon = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("切换音乐平台") == true }
                    iconBounds = icon.boundsInWindow
                    var clickable: SemanticsNode? = icon
                    while (clickable != null && clickable.config.getOrNull(SemanticsActions.OnClick) == null) clickable = clickable.parent
                    assertTrue(requireNotNull(clickable).config[SemanticsActions.OnClick].action?.invoke() == true)
                }
                Thread.sleep(70)
                screenshot("platform-opening-$isDark")
                Thread.sleep(650)
                screenshot("platform-open-$isDark")
                instrumentation.runOnMainSync {
                    assertTrue("Platform menu must actually expand", nodes().any {
                        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "QQ音乐" } == true
                    })
                }
                instrumentation.uiAutomation.executeShellCommand("input keyevent 4").close()
                Thread.sleep(100)
                screenshot("platform-closing-$isDark")
                Thread.sleep(600)
                screenshot("platform-closed-$isDark")
                instrumentation.runOnMainSync {
                    val icon = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("切换音乐平台") == true }
                    assertEquals(iconBounds.center.x, icon.boundsInWindow.center.x, 1f)
                    assertEquals(iconBounds.center.y, icon.boundsInWindow.center.y, 1f)
                    assertEquals(iconBounds.width, icon.boundsInWindow.width, 1f)
                }
                instrumentation.runOnMainSync { refreshing.value = true }
                Thread.sleep(800)
                screenshot("refresh-$isDark")
                instrumentation.runOnMainSync {
                    val label = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "正在刷新..." } == true }
                    assertTrue("Refresh header must be below the app bar", label.boundsInWindow.top >= barBottom)
                    refreshing.value = false
                }
                Thread.sleep(1000)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
