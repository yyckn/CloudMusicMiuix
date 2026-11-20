package com.hypermusic.miuix.ui.components

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.ui.theme.LocalEnableBlur
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.material.LocalSoftGlassEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class GaussianTopBarDeviceTest {
    @Test fun upperAndLowerBarBothSampleTheScrolledPage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ComponentActivity
        val blue = mutableStateOf(false)
        val enableBlur = mutableStateOf(true)
        var origin = Offset.Zero
        var width = 0
        var height = 0
        var viewportInset = 0.dp
        var scrollInset = 0.dp
        var totalInset = 0.dp
        instrumentation.runOnMainSync {
            activity.setContent {
                MiuixTheme {
                    CompositionLocalProvider(LocalSoftGlassEnabled provides false, LocalEnableBlur provides enableBlur.value) {
                        val backdrop = rememberPageBlurBackdrop()
                        val behavior = MiuixScrollBehavior()
                        Scaffold(topBar = {
                            BlurredBar(backdrop) {
                                TopAppBar(title = "模糊测试", largeTitle = "模糊测试", scrollBehavior = behavior,
                                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                                    modifier = Modifier.onGloballyPositioned {
                                        origin = it.localToScreen(Offset.Zero)
                                        width = it.size.width
                                        height = it.size.height
                                    })
                            }
                        }) { padding ->
                            totalInset = padding.calculateTopPadding()
                            viewportInset = pageViewportTopInset(totalInset)
                            scrollInset = pageScrollTopInset(totalInset)
                            LazyColumn(state = rememberLazyListState(initialFirstVisibleItemIndex = 20),
                                modifier = Modifier.fillMaxSize().padding(top = viewportInset)
                                    .nestedScroll(behavior.nestedScrollConnection).pageLayerBackdrop(backdrop),
                                contentPadding = PaddingValues(top = scrollInset)) {
                                items(80) { Box(Modifier.fillMaxWidth().height(40.dp)
                                    .background(if (blue.value) Color.Blue else Color.Red)) }
                            }
                        }
                    }
                }
            }
        }
        try {
            Thread.sleep(700)
            instrumentation.runOnMainSync {
                assertEquals(0.dp, viewportInset)
                assertEquals(totalInset, scrollInset)
            }
            val red = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            instrumentation.runOnMainSync { blue.value = true }
            Thread.sleep(700)
            val blueImage = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                // Sample away from title glyphs, in both halves of the complete app bar.
                for (fraction in listOf(.3f, .75f)) {
                    val x = (origin.x + width * .8f).toInt()
                    val y = (origin.y + height * fraction).toInt()
                    val a = red.getPixel(x, y)
                    val b = blueImage.getPixel(x, y)
                    assertTrue("Upper/lower background must react at y=$fraction: $a / $b",
                        android.graphics.Color.red(a) - android.graphics.Color.red(b) > 8 &&
                            android.graphics.Color.blue(b) - android.graphics.Color.blue(a) > 8)
                }
            } finally { red.recycle(); blueImage.recycle() }
            instrumentation.runOnMainSync { enableBlur.value = false }
            Thread.sleep(400)
            instrumentation.runOnMainSync {
                assertEquals(totalInset, viewportInset)
                assertEquals(0.dp, scrollInset)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
