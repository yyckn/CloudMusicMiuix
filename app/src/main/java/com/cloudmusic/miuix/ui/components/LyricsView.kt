package com.cloudmusic.miuix.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudmusic.miuix.data.model.LyricLine
import com.cloudmusic.miuix.data.model.SongLyrics
import com.cloudmusic.miuix.ui.theme.ThemeSettings
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricsView(
    lyrics: SongLyrics?,
    currentPosition: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics == null || lyrics.lines.isEmpty()) {
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

    val listState = rememberLazyListState()
    var userScrolling by remember { mutableStateOf(false) }
    
    // 动画持续时间（毫秒）
    val animationDuration = 300L
    
    // 计算当前应该高亮的歌词行索引
    // 提前 animationDuration 开始切换，使动画完成时正好是歌词时间戳开始
    val currentIndex = lyrics.lines.indexOfLast { it.startAt <= currentPosition + animationDuration }

    // 自动滚动到当前歌词（只在非用户手动滚动时）
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && !userScrolling) {
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = 0
            )
        }
    }

    // 检测用户是否在手动滚动
    LaunchedEffect(listState.isScrollInProgress) {
        userScrolling = listState.isScrollInProgress
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isActive = index == currentIndex
            
            // 计算相对于当前行的距离，用于设置延迟时间
            val distanceFromCurrent = if (index > currentIndex) index - currentIndex else 0
            val delayMs = distanceFromCurrent * 300 // 每行延迟300ms
            
            // 使用延迟的alpha动画
            var targetAlpha by remember { mutableStateOf(0.4f) }
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(
                    durationMillis = 300,
                    delayMillis = delayMs
                ),
                label = "lyric_alpha"
            )
            
            // 当isActive变化时更新targetAlpha
            LaunchedEffect(isActive) {
                targetAlpha = if (isActive) 1f else 0.4f
            }

            LyricLineItem(
                line = line,
                isActive = isActive,
                alpha = alpha,
                onClick = {
                    onSeekTo(line.startAt)
                },
                modifier = Modifier.padding(vertical = 0.dp, horizontal = 0.dp)
            )
        }
    }
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lyricFontSize = ThemeSettings.lyricFontSize
    val translationRatio = ThemeSettings.lyricTranslationRatio / 100f
    val translationFontSize = (lyricFontSize * translationRatio).toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 2.dp, horizontal = 2.dp)
            .graphicsLayer {
                this.alpha = alpha
            }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // 原文歌词
        Text(
            text = line.text,
            fontSize = lyricFontSize.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) {
                Color.White
            } else {
                Color.White.copy(alpha = 0.75f)
            },
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        
        // 翻译歌词（在原文下方，不重叠）
        if (line.translation.isNotEmpty()) {
            Text(
                text = line.translation,
                fontSize = translationFontSize.sp,
                color = if (isActive) {
                    Color.White.copy(alpha = 0.8f)
                } else {
                    Color.White.copy(alpha = 0.5f)
                },
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}
