package com.hypermusic.miuix.ui.components

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class MainTabPagerDeviceTest {
    @Test fun clicksAndSwipesStaySynchronizedWithoutStealingHorizontalCardScrolling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        val selected = mutableIntStateOf(0)
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
        fun click(text: String) {
            instrumentation.runOnMainSync {
                var target: SemanticsNode? = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == text } == true }
                while (target != null && target.config.getOrNull(SemanticsActions.OnClick) == null) target = target.parent
                assertTrue(requireNotNull(target).config[SemanticsActions.OnClick].action!!.invoke())
            }
            Thread.sleep(650)
        }
        fun bounds(tag: String): Rect {
            var rect = Rect.Zero
            instrumentation.runOnMainSync { rect = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == tag }.boundsInWindow }
            return rect
        }
        fun swipe(rect: Rect, left: Boolean, yFraction: Float = .75f) {
            val start = rect.left + rect.width * if (left) .85f else .15f
            val end = rect.left + rect.width * if (left) .15f else .85f
            val y = rect.top + rect.height * yFraction
            instrumentation.uiAutomation.executeShellCommand("input swipe ${start.toInt()} ${y.toInt()} ${end.toInt()} ${y.toInt()} 300")
                .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
            Thread.sleep(650)
        }
        try {
            instrumentation.runOnMainSync { activity.setContent {
                MiuixTheme {
                    Column(Modifier.fillMaxSize()) {
                        Row { (0..2).forEach { page -> TextButton("导航$page", onClick = { selected.intValue = page }) } }
                        MainTabPager(selected.intValue, { selected.intValue = it }, Modifier.weight(1f)) { page ->
                            var count by rememberSaveable { mutableIntStateOf(0) }
                            val row = rememberLazyListState()
                            Column(Modifier.fillMaxSize()) {
                                Text(if (LocalMainTabActive.current) "活动页$page" else "相邻页$page")
                                TextButton("计数$page:$count", onClick = { count++ })
                                LazyRow(Modifier.fillMaxWidth().height(80.dp).testTag("horizontal-row-$page"), state = row) {
                                    items((0..15).toList()) { index -> Text("卡片$page-$index", Modifier.width(200.dp).height(80.dp)) }
                                }
                                Text("横向位置$page:${row.firstVisibleItemIndex}")
                            }
                        }
                    }
                }
            } }
            Thread.sleep(700)
            click("计数0:0")
            click("导航2")
            assertEquals(2, selected.intValue)
            instrumentation.runOnMainSync { assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "活动页2" } == true }) }
            click("导航0")
            instrumentation.runOnMainSync { assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "计数0:1" } == true }) }
            instrumentation.runOnMainSync { selected.intValue = 1 }
            Thread.sleep(80)
            instrumentation.runOnMainSync { selected.intValue = 2 }
            Thread.sleep(650)
            assertEquals(2, selected.intValue)
            instrumentation.runOnMainSync { assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "活动页2" } == true }) }
            click("导航0")
            swipe(bounds("horizontal-row-0"), left = true, yFraction = .5f)
            assertEquals("A horizontal card row must consume its own drag", 0, selected.intValue)
            instrumentation.runOnMainSync { assertFalse(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "横向位置0:0" } == true }) }
            swipe(bounds("main-tab-pager"), left = true)
            assertEquals(1, selected.intValue)
            swipe(bounds("main-tab-pager"), left = true)
            assertEquals(2, selected.intValue)
            swipe(bounds("main-tab-pager"), left = true)
            assertEquals(2, selected.intValue)
            swipe(bounds("main-tab-pager"), left = false)
            assertEquals(1, selected.intValue)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
