package com.musichub.miuix.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musichub.miuix.data.model.LyricLine
import com.musichub.miuix.data.model.SongLyrics
import com.musichub.miuix.ui.theme.ThemeSettings
import com.musichub.miuix.ui.theme.rememberPlayerDark
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricsView(
    lyrics: SongLyrics?,
    currentPosition: Long,
    onSeekTo: (Long) -> Unit,
    isPlaying: Boolean = false,
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

    // 计算应该滚动到的索引（跳过歌手行）
    // 如果当前行是歌手行，不执行滚动（返回-1）
    // 如果当前行不是歌手行，执行滚动
    val scrollToIndex = if (currentIndex >= 0 && !lyrics.lines[currentIndex].isArtistLine) {
        currentIndex
    } else {
        -1
    }

    // 歌词区域高度和当前行高度（用于计算滚动偏移）
    var lyricsAreaHeight by remember { mutableStateOf(0) }
    var currentLineHeight by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    // 当歌词数据变化时（切换歌曲），滚动到顶部
    LaunchedEffect(lyrics) {
        listState.scrollToItem(0)
        userScrolling = false
    }

    // 自动滚动到当前歌词（只在非用户手动滚动时）
    LaunchedEffect(scrollToIndex) {
        if (scrollToIndex >= 0 && !userScrolling) {
            // 滚动到当前行，使用偏移让它居中
            // 由于顶部有空白区域，需要计算正确的偏移量
            // index + 1 是因为顶部有一个空白item
            val targetIndex = scrollToIndex + 1
            // 使用animateScrollToItem并设置负的scrollOffset让当前行向下偏移
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = -(lyricsAreaHeight / 2 - currentLineHeight / 2)
            )
        }
    }

    // 检测用户是否在手动滚动
    LaunchedEffect(listState.isScrollInProgress) {
        userScrolling = listState.isScrollInProgress
    }

    // 检查是否需要显示等待歌词
    // 找到第一句正文歌词（跳过歌手行、纯音乐等）
    val firstRealLyric = lyrics.lines.firstOrNull { 
        !it.isArtistLine && it.text.isNotBlank() && it.text != "纯音乐，请欣赏"
    }
    val firstLyricTime = firstRealLyric?.startAt ?: 0L
    val showWaitingDots = firstLyricTime > 10000

    android.util.Log.d("LyricsView", "lyrics.lines.size=${lyrics.lines.size}, firstLyricTime=$firstLyricTime, showWaitingDots=$showWaitingDots, currentPosition=$currentPosition, isPlaying=$isPlaying")

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                lyricsAreaHeight = coordinates.size.height
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部空白，高度为歌词区域的一半
        item {
            Spacer(modifier = Modifier.height(with(density) { (lyricsAreaHeight / 2).toDp() }))
        }
        
        itemsIndexed(lyrics.lines) { index, line ->
            // 检查是否是第一行空歌词，且需要显示等待歌词
            // 如果需要显示等待歌词，第一行视为等待歌词行
            // 如果不需要显示等待歌词（<=10秒），第一行显示为空格
            val isFirstLine = index == 0
            val isFirstEmptyLine = isFirstLine && (line.text.isBlank() || line.startAt == 0L || showWaitingDots || firstLyricTime in 1..10000)
            val showWaitingDotsHere = showWaitingDots && isFirstEmptyLine
            
            // 如果当前行是歌手行，不高亮显示
            // 如果是等待歌词行，在第一句歌词到达前视为活动状态
            val isActive = if (showWaitingDotsHere) {
                currentPosition < firstLyricTime
            } else {
                index == currentIndex && !line.isArtistLine
            }
            
            // 计算相对于当前行的距离，用于设置延迟时间
            val distanceFromCurrent = if (index > currentIndex) index - currentIndex else 0
            val delayMs = distanceFromCurrent * 300 // 每行延迟300ms
            
            // 使用延迟的alpha动画
            var targetAlpha by remember { mutableStateOf(0.4f) }
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(
                    durationMillis = 600,
                    delayMillis = delayMs
                ),
                label = "lyric_alpha"
            )
            
            // 当isActive变化时更新targetAlpha
            LaunchedEffect(isActive) {
                targetAlpha = if (isActive) 1f else 0.4f
            }

            if (showWaitingDotsHere) {
                // 替换空歌词行为等待歌词
                WaitingLyricDots(
                    firstLyricTime = firstLyricTime,
                    currentPosition = currentPosition,
                    isPlaying = isPlaying,
                    baseFontSize = ThemeSettings.lyricFontSize.sp,
                    onClick = { onSeekTo(0L) },
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            if (isActive) {
                                currentLineHeight = coordinates.size.height
                            }
                        }
                )
            } else if (isFirstLine && firstLyricTime in 1..10000) {
                // 第一句歌词时间 <= 10秒，显示一个空格的歌词行
                LyricLineItem(
                    line = line.copy(text = " "),
                    isActive = isActive,
                    alpha = alpha,
                    onClick = { onSeekTo(line.startAt) },
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            if (isActive) {
                                currentLineHeight = coordinates.size.height
                            }
                        }
                )
            } else {
                LyricLineItem(
                    line = line,
                    isActive = isActive,
                    alpha = alpha,
                    onClick = {
                        onSeekTo(line.startAt)
                    },
                    modifier = Modifier
                        .padding(vertical = 0.dp, horizontal = 0.dp)
                        .onGloballyPositioned { coordinates ->
                            if (isActive) {
                                currentLineHeight = coordinates.size.height
                            }
                        }
                )
            }
        }
        
        // 底部空白，高度为歌词区域的一半
        item {
            Spacer(modifier = Modifier.height(with(density) { (lyricsAreaHeight / 2).toDp() }))
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
    val lyricCenter = ThemeSettings.lyricAlignmentIndex == 1
    val textAlign = if (lyricCenter) TextAlign.End else TextAlign.Start

    // 特殊情况：只有一个"合"或"童声"
    val specialKeywords = listOf("合", "童声")
    val trimmedText = line.text.trim()
    val contentWithoutBrackets = trimmedText
        .removeSurrounding("（", "）")
        .removeSurrounding("(", ")")
        .removeSurrounding("【", "】")
        .trim()
    val isSpecialArtistLine = line.isArtistLine && contentWithoutBrackets in specialKeywords

    // 判断是否为歌手行
    val isArtist = line.isArtistLine

    // 歌手行使用翻译字体大小
    val displayFontSize = if (isArtist) translationFontSize.sp else lyricFontSize.sp

    // 文字颜色
    val playerText = if (rememberPlayerDark()) Color.White else Color.Black
    val textColor = if (isActive) {
        playerText
    } else {
        playerText.copy(alpha = 0.75f)
    }

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
        horizontalAlignment = if (lyricCenter) Alignment.End else Alignment.Start
    ) {
        // 歌词内容
        if (isSpecialArtistLine) {
            // 特殊歌手行：统一成"（合）"或"（童声）"，不加图标
            val displayText = "（$contentWithoutBrackets）"
            Text(
                text = displayText,
                fontSize = displayFontSize,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isArtist) {
            // 歌手行：在括号内前面加图标
            val displayText = formatArtistLineText(line.text)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = MiuixIcons.ContactsCircle,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size((displayFontSize.value * 1.2).dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = displayText,
                    fontSize = displayFontSize,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                    textAlign = textAlign,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // 普通行
            Text(
                text = line.text,
                fontSize = displayFontSize,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // 翻译歌词（在原文下方，不重叠）
        if (line.translation.isNotEmpty() && !isArtist) {
            Text(
                text = line.translation,
                fontSize = translationFontSize.sp,
                color = if (isActive) {
                    playerText.copy(alpha = 0.8f)
                } else {
                    playerText.copy(alpha = 0.5f)
                },
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}

/**
 * 格式化歌手行文字，统一括号为中文括号，去掉冒号
 */
private fun formatArtistLineText(text: String): String {
    var result = text.trim()
    // 统一括号为中文括号
    result = result.replace("(", "（").replace(")", "）")
    result = result.replace("[", "【").replace("]", "】")
    // 去掉末尾的冒号（中英文）
    if (result.endsWith("：")) {
        result = result.substring(0, result.length - 1).trim()
    } else if (result.endsWith(":")) {
        result = result.substring(0, result.length - 1).trim()
    }
    return result
}

/**
 * 等待歌词圆圈组件
 * 当第一句歌词时间戳大于10秒时显示
 */
@Composable
fun WaitingLyricDots(
    firstLyricTime: Long,
    currentPosition: Long,
    isPlaying: Boolean,
    baseFontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 确定圆圈数量：大于10秒显示3个，小于等于10秒不显示
    val dotCount = when {
        firstLyricTime <= 10000 -> return
        else -> 3
    }

    // 每个圆圈的时长
    val dotDuration = firstLyricTime / dotCount

    // 第一句歌词到达前500ms开始淡出动画
    val fadeOutStartTime = firstLyricTime - 500
    val isFinished = currentPosition >= fadeOutStartTime
    val targetAlpha = if (isFinished) 0f else 1f
    val overallAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 500),
        label = "waiting_dots_alpha"
    )

    val lyricCenter = ThemeSettings.lyricAlignmentIndex == 1
    val playerDotsColor = if (rememberPlayerDark()) Color.White else Color.Black

    // 如果完全透明，仍然渲染但不可见，以便点击跳转
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 2.dp, horizontal = 2.dp)
            .graphicsLayer { this.alpha = overallAlpha }
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = if (lyricCenter) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 动画参数
            val firstAnimDuration = 1250L // 第一次动画时长
            val loopDuration = 2500L // 每次循环动画时长

            for (i in 0 until dotCount) {
                val dotStartTime = i * dotDuration
                val isActive = currentPosition >= dotStartTime
                val elapsedInDot = if (isActive) currentPosition - dotStartTime else 0L

                // 计算循环次数（四舍五入）
                val remainingAfterFirst = dotDuration - firstAnimDuration
                val numLoops = if (remainingAfterFirst > 0) {
                    Math.round(remainingAfterFirst.toFloat() / loopDuration.toFloat()).toInt()
                } else {
                    0
                }

                // 总动画时长
                val totalAnimDuration = firstAnimDuration + numLoops * loopDuration

                // 判断是否应该执行动画
                val shouldAnimate = isActive && elapsedInDot < totalAnimDuration

                // 计算动画进度
                val dotScale: Float
                val dotColor: Color

                if (shouldAnimate) {
                    if (elapsedInDot < firstAnimDuration) {
                        // 第一次动画：0.8倍 → 1倍，半透明白色 → 白色
                        val progress = elapsedInDot.toFloat() / firstAnimDuration.toFloat()
                        dotScale = 0.8f + 0.2f * progress
                        dotColor = androidx.compose.ui.graphics.lerp(playerDotsColor.copy(alpha = 0.4f), playerDotsColor, progress)
                    } else {
                        // 循环动画：1倍 → 0.8倍 → 1倍，白色 → 半透明白色 → 白色
                        val loopElapsed = elapsedInDot - firstAnimDuration
                        val loopProgress = ((loopElapsed % loopDuration).toFloat() / loopDuration.toFloat()).coerceIn(0f, 1f)
                        val animProgress = kotlin.math.sin(loopProgress * kotlin.math.PI.toFloat())
                        // sin(0)=0, sin(π/2)=1, sin(π)=0 → 1x → 0.8x → 1x
                        dotScale = 1f - 0.2f * animProgress
                        // 颜色同步：白色 → 半透明白色 → 白色
                        dotColor = androidx.compose.ui.graphics.lerp(playerDotsColor, playerDotsColor.copy(alpha = 0.4f), animProgress)
                    }
                } else if (isActive) {
                    // 动画完成，保持最终状态：1倍，白色
                    dotScale = 1f
                    dotColor = playerDotsColor
                } else {
                    // 未开始状态：0.8倍，半透明白色
                    dotScale = 0.8f
                    dotColor = playerDotsColor.copy(alpha = 0.4f)
                }

                // 固定宽度容器，防止字号变化导致后面的圆圈移动
                Box(
                    modifier = Modifier.width((baseFontSize.value * 0.8f).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "●",
                        fontSize = baseFontSize * dotScale,
                        color = dotColor
                    )
                }

                if (i < dotCount - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            // 右侧空格，字体大小1.2倍
            Text(
                text = " ",
                fontSize = baseFontSize * 1.2f,
                color = Color.Transparent
            )
        }
    }
}
