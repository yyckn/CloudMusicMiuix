package com.musichub.miuix.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.ui.composable.list.rememberLyricsLazyListState
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.LyricsAnchor
import com.musichub.miuix.data.lyrics.AppleMusicLyricsConverter
import com.musichub.miuix.data.model.SongLyrics
import com.musichub.miuix.ui.theme.AppleMusicLyricsFontFamily
import com.musichub.miuix.ui.theme.ThemeSettings
import com.musichub.miuix.ui.theme.rememberPlayerDark
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.isActive

/**
 * 基于 accompanist-lyrics-ui 的 Apple Music 风格歌词渲染。
 */
@Composable
fun AppleMusicLyricsView(
    lyrics: SongLyrics?,
    currentPosition: Long,
    onSeekTo: (Long) -> Unit,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    positionProvider: (() -> Long)? = null,
) {
    val dark = rememberPlayerDark()
    val wordByWord = ThemeSettings.wordByWordLyrics
    val synced = remember(lyrics, wordByWord) {
        lyrics?.let { AppleMusicLyricsConverter.convert(it, wordByWord = wordByWord) }
    }

    if (synced == null || synced.lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无歌词",
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    // 播放位置以逐帧状态驱动歌词引擎：避免 100ms 轮询造成的卡顿
    val livePosition = remember { mutableLongStateOf(currentPosition) }
    val latestProvider by rememberUpdatedState(positionProvider)
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val provider = latestProvider ?: continue
            val position = provider()
            if (position != livePosition.longValue) {
                livePosition.longValue = position
            }
        }
    }
    // 暂停时仍跟随 playerState 的位置变化（例如拖动进度条），忽略 100ms 内的抖动
    if (!isPlaying) {
        SideEffect {
            if (kotlin.math.abs(livePosition.longValue - currentPosition) > 250L) {
                livePosition.longValue = currentPosition
            }
        }
    }

    key(lyrics) {
        val listState = rememberLyricsLazyListState()
        val fontSize = ThemeSettings.lyricFontSize
        val translationRatio = ThemeSettings.lyricTranslationRatio / 100f
        val fontFamily = AppleMusicLyricsFontFamily
        val normalStyle = TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            lineHeight = TextUnit.Unspecified,
            fontWeight = FontWeight.Bold,
            textMotion = TextMotion.Animated,
        )
        val accompanimentStyle = normalStyle.copy(fontSize = (fontSize * 0.7f).sp)
        val captionStyle = TextStyle(
            fontFamily = fontFamily,
            fontSize = (fontSize * translationRatio).sp,
            lineHeight = TextUnit.Unspecified,
            fontWeight = FontWeight.Normal,
            textMotion = TextMotion.Animated,
        )
        val textColor = if (dark) Color.White else Color.Black

        KaraokeLyricsView(
            listState = listState,
            lyrics = synced,
            currentPosition = { livePosition.longValue.toInt() },
            onLineClicked = { onSeekTo(it.start.toLong()) },
            onLinePressed = { },
            modifier = modifier.overflowHorizontally(AM_INTERNAL_PADDING_REDUCTION),
            normalLineTextStyle = normalStyle,
            accompanimentLineTextStyle = accompanimentStyle,
            phoneticTextStyle = captionStyle,
            textColor = textColor,
            blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver,
            anchor = LyricsAnchor.Fraction(0.35f),
        )
    }
}

// 库内部每行硬编码 16dp 左右内边距，这里让歌词视图向两侧各溢出 10dp 把内边距收到约 6dp。
private val AM_INTERNAL_PADDING_REDUCTION = 10.dp

/**
 * 让内容向左右各溢出 [extra]，但自身仍占用原有宽度。
 */
private fun Modifier.overflowHorizontally(extra: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx()
    val minWidth =
        if (constraints.minWidth == Constraints.Infinity) constraints.minWidth
        else constraints.minWidth + extraPx * 2
    val maxWidth =
        if (constraints.maxWidth == Constraints.Infinity) constraints.maxWidth
        else constraints.maxWidth + extraPx * 2
    val placeable = measurable.measure(constraints.copy(minWidth = minWidth, maxWidth = maxWidth))
    val reportWidth =
        if (constraints.maxWidth == Constraints.Infinity) placeable.width else constraints.maxWidth
    layout(reportWidth, constraints.maxHeight) {
        placeable.place(-extraPx, 0)
    }
}
