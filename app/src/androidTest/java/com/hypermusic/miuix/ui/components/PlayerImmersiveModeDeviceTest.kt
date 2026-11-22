package com.hypermusic.miuix.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.ui.theme.ThemeSettings
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerImmersiveModeDeviceTest {
    @Test fun barsRestoreOnDisableAndPlayerExitAndThePreferenceDefaultsOff() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        val prefs = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
        val key = "player_immersive_mode"
        val existed = prefs.contains(key)
        val previous = prefs.getBoolean(key, false)
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ComponentActivity
        val enabled = mutableStateOf(false)
        val playerVisible = mutableStateOf(true)
        var contentBounds = Rect.Zero
        val types = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        fun assertBars(visible: Boolean) {
            Thread.sleep(650)
            instrumentation.runOnMainSync {
                val insets = requireNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView))
                assertEquals("Status bar", visible, insets.isVisible(WindowInsetsCompat.Type.statusBars()))
                assertEquals("Navigation bar", visible, insets.isVisible(WindowInsetsCompat.Type.navigationBars()))
            }
        }
        try {
            instrumentation.runOnMainSync {
                prefs.edit().remove(key).commit()
                ThemeSettings.init(context)
                assertFalse(ThemeSettings.playerImmersiveMode)
                ThemeSettings.updatePlayerImmersiveMode(true)
                ThemeSettings.init(context)
                assertTrue(ThemeSettings.playerImmersiveMode)
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(types)
                activity.setContent {
                    PlayerImmersiveMode(playerVisible.value && enabled.value)
                    Box(Modifier.fillMaxSize().windowInsetsPadding(playerSystemBarInsets())
                        .onGloballyPositioned { contentBounds = it.boundsInWindow() })
                }
            }
            assertBars(true)
            var originalBounds = Rect.Zero
            instrumentation.runOnMainSync { originalBounds = contentBounds }
            instrumentation.runOnMainSync { enabled.value = true }
            assertBars(false)
            instrumentation.runOnMainSync { assertEquals("Hiding system bars must preserve the player's safe-area spacing", originalBounds, contentBounds) }
            instrumentation.runOnMainSync { enabled.value = false }
            assertBars(true)
            instrumentation.runOnMainSync { assertEquals(originalBounds, contentBounds) }
            instrumentation.runOnMainSync { enabled.value = true }
            assertBars(false)
            instrumentation.runOnMainSync { playerVisible.value = false }
            assertBars(true)
        } finally {
            instrumentation.runOnMainSync {
                activity.finish()
                prefs.edit().apply { if (existed) putBoolean(key, previous) else remove(key) }.commit()
                ThemeSettings.init(context)
            }
        }
    }
}
