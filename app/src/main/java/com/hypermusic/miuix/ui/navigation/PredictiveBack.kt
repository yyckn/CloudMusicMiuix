package com.hypermusic.miuix.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.hypermusic.miuix.ui.theme.ThemeSettings
import kotlinx.coroutines.CancellationException

object PredictiveBackVisual {
    var progress by mutableFloatStateOf(0f)
    var edge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentBack by rememberUpdatedState(onBack)
    androidx.activity.compose.BackHandler(enabled = enabled && !ThemeSettings.predictiveBackEnabled) { currentBack() }
    PredictiveBackHandler(enabled = enabled && ThemeSettings.predictiveBackEnabled) { events ->
        try {
            events.collect { event ->
                PredictiveBackVisual.edge = event.swipeEdge
                PredictiveBackVisual.progress = event.progress.coerceIn(0f, 1f)
            }
            PredictiveBackVisual.progress = 0f
            currentBack()
        } catch (cancelled: CancellationException) {
            PredictiveBackVisual.progress = 0f
            throw cancelled
        } finally {
            PredictiveBackVisual.progress = 0f
        }
    }
}
