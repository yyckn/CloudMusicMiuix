package com.hypermusic.miuix.ui.components

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hypermusic.miuix.ui.theme.ThemeSettings

/** Hide system chrome without changing the player's existing safe-area geometry. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun playerSystemBarInsets(): WindowInsets = if (ThemeSettings.playerImmersiveMode)
    WindowInsets.systemBarsIgnoringVisibility else WindowInsets.systemBars

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun playerNavigationBarInsets(): WindowInsets = if (ThemeSettings.playerImmersiveMode)
    WindowInsets.navigationBarsIgnoringVisibility else WindowInsets.navigationBars

/** Follow the player's visibility, rather than the sheet's exit-animation composition. */
@Composable
internal fun PlayerImmersiveMode(enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) {
        var current = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        current as? Activity
    }
    DisposableEffect(activity, enabled) {
        if (activity == null || !enabled) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val previousBehavior = controller.systemBarsBehavior
        val barTypes = WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
        fun hideBars() {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(barTypes)
        }
        hideBars()
        val lifecycle = (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hideBars() }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            controller.show(barTypes)
            controller.systemBarsBehavior = previousBehavior
        }
    }
}
