package com.hypermusic.miuix.ui.components

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.viewmodel.PlayerState

/** Compare a single blur surface with two independently moving/laid-out adjacent surfaces. */
@RunWith(AndroidJUnit4::class)
class ContinuousBottomBlurDeviceTest {
    @Test fun realPlayerKeepsItsSharedTransitionSeparateFromTheSystemInset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(
            Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        ) as ComponentActivity
        val secondary = mutableStateOf(false)
        var transitionActive = false
        var heightWithNavigation = 0
        var heightWithInset = 0
        instrumentation.runOnMainSync {
            activity.setContent {
                MiuixTheme {
                    SharedTransitionLayout {
                        transitionActive = isTransitionActive
                        AnimatedContent(secondary.value, transitionSpec = {
                            slideInHorizontally(tween(500)) { it } togetherWith
                                slideOutHorizontally(tween(500)) { -it }
                        }) { detail ->
                            val source = rememberLayerBackdrop()
                            val blurred = rememberContinuousBottomBackdrop(source)
                            Box(Modifier.fillMaxSize()) {
                                Canvas(Modifier.fillMaxSize().continuousBackdropSource(source, blurred)) {
                                    drawRect(Brush.linearGradient(listOf(Color.Red, Color.Blue)))
                                }
                                Column(Modifier.align(Alignment.BottomCenter)) {
                                    PlayerBar(
                                        playerState = PlayerState(currentTrack = Track(
                                            "test", "共享过渡测试", "歌手", "", "专辑", "", sourceUrl = "", imgUrl = "",
                                        )),
                                        onTogglePlayPause = {},
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        hasBottomBar = !detail,
                                        blurBackdrop = blurred,
                                        modifier = Modifier.onGloballyPositioned {
                                            if (detail) heightWithInset = it.size.height
                                            else heightWithNavigation = it.size.height
                                        },
                                    )
                                    if (!detail) Spacer(Modifier.height(80.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        try {
            Thread.sleep(600)
            for (detail in listOf(true, false)) {
                instrumentation.runOnMainSync { secondary.value = detail }
                Thread.sleep(150)
                instrumentation.runOnMainSync { assertTrue("Shared player transition must run", transitionActive) }
                Thread.sleep(700)
                instrumentation.runOnMainSync {
                    assertTrue("Transition must finish", !transitionActive)
                    assertTrue("System inset is laid out separately", heightWithInset >= heightWithNavigation && heightWithNavigation > 0)
                }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test fun adjacentSurfacesMatchOneContinuousSurfaceInLightAndDarkModes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(
            Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        ) as ComponentActivity
        val split = mutableStateOf(false)
        val dark = mutableStateOf(false)
        var origin = Offset.Zero
        var width = 0
        var height = 0
        instrumentation.runOnMainSync {
            activity.setContent {
                val backdrop = rememberLayerBackdrop()
                val blurred = requireNotNull(rememberContinuousBottomBackdrop(backdrop))
                Box(Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize().continuousBackdropSource(backdrop, blurred)) {
                        drawRect(Brush.linearGradient(listOf(Color.Red, Color.Blue, Color.Green)))
                        for (i in 0..80) {
                            drawRect(
                                if (i % 2 == 0) Color.White else Color.Black,
                                topLeft = Offset(0f, i * 19f),
                                size = androidx.compose.ui.geometry.Size(size.width, 7f),
                            )
                        }
                    }
                    val tint = (if (dark.value) Color(0xFF252529) else Color(0xFFECECF0)).copy(alpha = .72f)
                    Column(Modifier.offset(60.dp, 140.dp).width(260.dp).height(160.dp)
                        .onGloballyPositioned {
                            origin = it.localToScreen(Offset.Zero)
                            width = it.size.width
                            height = it.size.height
                        }) {
                        if (split.value) {
                            Box(Modifier.fillMaxWidth().height(103.dp).continuousBottomBlur(blurred, tint))
                            Box(Modifier.fillMaxWidth().weight(1f).continuousBottomBlur(blurred, tint))
                        } else {
                            Box(Modifier.fillMaxSize().continuousBottomBlur(blurred, tint))
                        }
                    }
                }
            }
        }
        try {
            for (isDark in listOf(false, true)) {
                instrumentation.runOnMainSync { dark.value = isDark; split.value = false }
                Thread.sleep(700)
                val continuous = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                instrumentation.runOnMainSync { split.value = true }
                Thread.sleep(700)
                val adjacent = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                var maxDifference = 0
                var changed = 0
                val rowErrors = DoubleArray(height)
                // Ignore only the outermost two pixels; include both sides of the internal seam.
                for (y in 2 until height - 2) for (x in 2 until width - 2) {
                    val a = continuous.getPixel(origin.x.toInt() + x, origin.y.toInt() + y)
                    val b = adjacent.getPixel(origin.x.toInt() + x, origin.y.toInt() + y)
                    val difference = maxOf(
                        kotlin.math.abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)),
                        kotlin.math.abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)),
                        kotlin.math.abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)),
                    )
                    maxDifference = maxOf(maxDifference, difference)
                    rowErrors[y] += difference.toDouble() / (width - 4)
                    if (difference > 2) changed++
                }
                continuous.recycle()
                adjacent.recycle()
                // GPU offscreen compositing can differ by a few 8-bit rounding levels. A
                // boundary-specific jump is the regression: compare error directly across it.
                val seam = (height * 103f / 160f).toInt()
                val seamJump = kotlin.math.abs(rowErrors[seam - 1] - rowErrors[seam + 1])
                assertTrue("dark=$isDark max=$maxDifference changed=$changed seamJump=$seamJump",
                    maxDifference <= 4 && seamJump < 1.0)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
