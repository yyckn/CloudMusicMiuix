package com.hypermusic.miuix.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalCoverTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalCoverVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCover(key: String?): Modifier {
    val transition = LocalCoverTransitionScope.current
    val visibility = LocalCoverVisibilityScope.current
    if (key.isNullOrBlank() || transition == null || visibility == null) return this
    return with(transition) {
        sharedElement(
            rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(12.dp))
        )
    }
}

fun playlistCoverKey(id: String): String = "playlist_cover_$id"
fun albumCoverKey(id: Long, isQq: Boolean): String = "album_cover_${if (isQq) "qq" else "netease"}_$id"
