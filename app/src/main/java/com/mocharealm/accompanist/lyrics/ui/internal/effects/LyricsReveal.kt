package com.mocharealm.accompanist.lyrics.ui.internal.effects

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.constrainHeight
import kotlin.math.roundToInt

/** Transition owns size and effects together, so exit content survives until both have settled. */
@Composable
internal fun LyricsReveal(
    visible: Boolean,
    animateInitial: Boolean = false,
    origin: TransformOrigin = TransformOrigin.Center,
    backingVocal: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val blur =
        remember(density, backingVocal) {
            Array<RenderEffect?>(if (backingVocal) 1 else 65) {
                if (it == 0) null
                else
                    BlurEffect(
                        8f * density.density * it / 64f,
                        8f * density.density * it / 64f,
                        TileMode.Decal,
                    )
            }
        }
    val visibility = remember { MutableTransitionState(if (animateInitial) false else visible) }
    visibility.targetState = visible
    val transition = rememberTransition(visibility, label = "lyricsVisibility")
    val progress =
        transition.animateFloat(transitionSpec = {
            if (backingVocal) tween(200, easing = Easing { it * it * (3f - 2f * it) }) else LyricsRevealSpring
        }, label = "lyricsReveal") {
            if (it) 1f else 0f
        }
    // Retain the fixed-size content until this same progress has completely settled.
    // Height and effects share one spring, including interrupted/reversed transitions.
    if (visibility.currentState || visibility.targetState) {
        Box(
            Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    val height =
                        constraints.constrainHeight(
                            (placeable.height * progress.value.coerceIn(0f, 1f)).roundToInt()
                        )
                    val offset = ((height - placeable.height) * origin.pivotFractionY).roundToInt()
                    val top =
                        placeable[LyricsVisualTop].let {
                            if (it == AlignmentLine.Unspecified) 0 else minOf(0, it)
                        }
                    val bottom =
                        placeable[LyricsVisualBottom].let {
                            if (it == AlignmentLine.Unspecified) placeable.height
                            else maxOf(placeable.height, it)
                        }
                    layout(
                        placeable.width,
                        height,
                        mapOf(
                            LyricsVisualTop to top + offset,
                            LyricsVisualBottom to bottom + offset,
                        ),
                    ) {
                        placeable.place(0, offset)
                    }
                }
                .lyricsVisualLayer {
                    val value = progress.value
                    transformOrigin = origin
                    scaleX = if (backingVocal) value.coerceIn(0f, 1f) else revealScale(value)
                    scaleY = scaleX
                    alpha = if (backingVocal) value.coerceIn(0f, 1f) else revealAlpha(value)
                    renderEffect = if (backingVocal) null else blur[revealBlurIndex(value)]
                }
        ) {
            content()
        }
    }
}
