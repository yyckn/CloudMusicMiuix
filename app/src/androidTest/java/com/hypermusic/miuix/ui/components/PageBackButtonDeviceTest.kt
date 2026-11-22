package com.hypermusic.miuix.ui.components

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.material.SoftGlassTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class PageBackButtonDeviceTest {
    @Test fun arrowAndSurroundingButtonUseOneClickTargetInBothStyles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ComponentActivity
        val glass = mutableStateOf(true)
        var clicks = 0
        fun nodes(): List<SemanticsNode> {
            val result = mutableListOf<SemanticsNode>()
            fun read(node: SemanticsNode) { result += node; node.children.forEach(::read) }
            fun visit(view: android.view.View) {
                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" }?.let {
                    read((it.invoke(view) as SemanticsOwner).rootSemanticsNode)
                }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            visit(activity.window.decorView)
            return result
        }
        fun tap(x: Float, y: Float) {
            val down = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                event.recycle()
                Thread.sleep(100)
            }
            Thread.sleep(200)
        }
        instrumentation.runOnMainSync {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.setContent {
                MiuixTheme {
                    SoftGlassTheme(glass.value, false) {
                        val behavior = MiuixScrollBehavior()
                        SideEffect { behavior.state.contentOffset = -300f }
                        Scaffold(topBar = { SmallTopAppBar(title = "返回按钮测试", scrollBehavior = behavior,
                            navigationIcon = { PageBackButton(onClick = { clicks++ }) }) }) { _ ->
                            Box(Modifier.fillMaxSize().background(Color(0xFF83A5D7)))
                        }
                    }
                }
            }
        }
        try {
            for (soft in listOf(true, false)) {
                instrumentation.runOnMainSync { glass.value = soft }
                Thread.sleep(650)
                var rect = Rect.Zero
                val origin = IntArray(2)
                instrumentation.runOnMainSync {
                    var node: SemanticsNode? = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("返回") == true }
                    while (node != null && node.config.getOrNull(SemanticsActions.OnClick) == null) node = node.parent
                    rect = requireNotNull(node).boundsInWindow
                    val clicksInBounds = nodes().count { it.config.getOrNull(SemanticsActions.OnClick) != null &&
                        it.boundsInWindow.overlaps(rect) }
                    assertEquals("The navigation button must not have a second click layer", 1, clicksInBounds)
                    assertTrue(rect.width >= 38 * activity.resources.displayMetrics.density)
                    activity.window.decorView.getLocationOnScreen(origin)
                }
                val before = clicks
                tap(origin[0] + rect.center.x, origin[1] + rect.center.y)
                instrumentation.runOnMainSync { assertEquals(before + 1, clicks) }
                tap(origin[0] + rect.left + rect.width * .12f, origin[1] + rect.center.y)
                instrumentation.runOnMainSync { assertEquals("Tapping beside the arrow must also navigate back", before + 2, clicks) }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
