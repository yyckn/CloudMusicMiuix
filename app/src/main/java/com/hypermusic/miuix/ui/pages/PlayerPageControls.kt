package com.hypermusic.miuix.ui.pages

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.R
import com.hypermusic.miuix.data.audio.separation.VocalSeparationEngine
import com.hypermusic.miuix.data.audio.separation.VocalSeparationMix
import com.hypermusic.miuix.data.audio.separation.VocalSeparationModel
import com.hypermusic.miuix.data.audio.separation.VocalSeparationStatus
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.ui.theme.rememberPlayerDark
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.NotesFill
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PlayerVocalSeparationControl() {
    val ui by VocalSeparationEngine.uiState.collectAsState()
    if (!ui.enabled || ui.trackKey.isBlank()) return

    val textStyle = MiuixTheme.textStyles.footnote1
    val textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    BasicComponent(
        title = "人声分离",
        bottomAction = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (val status = ui.status) {
                    is VocalSeparationStatus.Preparing -> {
                        val progress = status.progress.coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(status.stage, style = textStyle, color = textColor)
                            Text("${(progress * 100).roundToInt()}%", style = textStyle, color = textColor)
                        }
                    }
                    is VocalSeparationStatus.Ready -> {
                        val model = when (VocalSeparationModel.byId(status.entry.modelId)) {
                            VocalSeparationModel.KUIELAB_B_VOCALS -> "快速"
                            VocalSeparationModel.KIM_VOCAL_2 -> "均衡"
                            VocalSeparationModel.UVR_MDX_INST_HQ_4 -> "质量"
                        }
                        val method = when (status.entry.backend) {
                            "GPU · WebGPU" -> "已使用GPU加速"
                            "NNAPI" -> "使用NNAPI推理"
                            "CPU" -> "使用CPU计算"
                            else -> "读取分离缓存"
                        }
                        val mix = ui.mix
                        val accompaniment = (mix.instrumentalGain * 100).roundToInt()
                        val vocals = (mix.vocalGain * 100).roundToInt()
                        Text(
                            "使用${model}模型分离，$method，当前人声${vocals}%、伴奏${accompaniment}%",
                            style = textStyle,
                            color = textColor,
                        )
                        Slider(
                            value = mix.position.toFloat(),
                            onValueChange = { VocalSeparationEngine.setMix(VocalSeparationMix(it.roundToInt())) },
                            valueRange = 0f..VocalSeparationMix.MAX_POSITION.toFloat(),
                            steps = VocalSeparationMix.MAX_POSITION - 1,
                            showKeyPoints = true,
                            keyPoints = listOf(0f, 5f, 10f, 15f, 20f),
                            hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                            Text("仅伴奏", Modifier.align(Alignment.CenterStart), style = textStyle, color = textColor)
                            Text("原曲", Modifier.align(Alignment.Center), style = textStyle, color = textColor)
                            Text("仅人声", Modifier.align(Alignment.CenterEnd), style = textStyle, color = textColor)
                        }
                    }
                    VocalSeparationStatus.MissingModel ->
                        Text("请在人声分离设置中下载${ui.model.label}模型", style = textStyle, color = textColor)
                    VocalSeparationStatus.Idle -> {
                        Text("当前歌曲还没有可用的分离结果", style = textStyle, color = textColor)
                        TextButton(text = "重新分离", onClick = VocalSeparationEngine::retry)
                    }
                    is VocalSeparationStatus.Failed -> {
                        Text(status.message, style = textStyle, color = MiuixTheme.colorScheme.error)
                        TextButton(text = "重试", onClick = VocalSeparationEngine::retry)
                    }
                }
            }
        },
    )
}

internal fun formatSpeedValue(speed: Float): String {
    val hundredths = (speed * 100f).roundToInt()
    return if (hundredths % 10 == 0) {
        String.format(java.util.Locale.US, "%.1f", speed)
    } else {
        String.format(java.util.Locale.US, "%.2f", speed)
    }
}

/** Local playback has no online account actions; car controls keep their existing arrangement. */
@Composable
internal fun LocalPlayerActions(
    color: Color,
    locked: Boolean,
    playMode: Int,
    onClose: () -> Unit,
    onPlayMode: () -> Unit,
    onPlaylist: () -> Unit,
    onEffects: () -> Unit,
    onMore: () -> Unit,
    onLyrics: (() -> Unit)?,
    lyricsSelected: Boolean
) {
    val tint = color.copy(alpha = 0.6f)
    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
        Icon(MiuixIcons.Close, "关闭", tint = tint, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.width(16.dp))
    if (onLyrics != null) {
        IconButton(onClick = onLyrics, modifier = Modifier.size(48.dp)) {
            Icon(if (lyricsSelected) MiuixIcons.NotesFill else MiuixIcons.Notes, "歌词",
                tint = if (lyricsSelected) color else tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
    }
    IconButton(onClick = onPlayMode, modifier = Modifier.size(48.dp).alpha(if (locked) 0.35f else 1f)) {
        Icon(when (playMode) { 1 -> MiuixIcons.Merge; 2 -> MiuixIcons.Replace; else -> MiuixIcons.Sort },
            when (playMode) { 1 -> "乱序播放"; 2 -> "单曲循环"; else -> "顺序播放" },
            tint = tint, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.width(16.dp))
    IconButton(onClick = onPlaylist, modifier = Modifier.size(48.dp).alpha(if (locked) 0.35f else 1f)) {
        Icon(MiuixIcons.Playlist, "播放列表", tint = tint, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.width(16.dp))
    if (onLyrics == null) {
        IconButton(onClick = onEffects, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.ic_md3_equalizer), "音效", tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
    }
    IconButton(onClick = onMore, modifier = Modifier.size(48.dp)) {
        Icon(MiuixIcons.More, "更多", tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun PlayerArtistAlbumText(track: Track?, color: Color, fontSize: TextUnit,
    onArtistClick: (Int) -> Unit, onAlbumClick: () -> Unit, modifier: Modifier = Modifier, centered: Boolean = false,
    maxLines: Int = 1) {
    val text = buildPlayerArtistAlbumText(track)
    ClickableText(text = text, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = fontSize, color = color.copy(alpha = 0.7f),
            textAlign = if (centered) TextAlign.Center else TextAlign.Start),
        onClick = { offset ->
            when {
                text.getStringAnnotations("artist", offset, offset).isNotEmpty() ->
                    onArtistClick(text.getStringAnnotations("artist", offset, offset).first().item.toIntOrNull() ?: 0)
                text.getStringAnnotations("album", offset, offset).isNotEmpty() -> onAlbumClick()
            }
        })
}

internal fun isPlayerArtistGroup(track: Track): Boolean =
    track.artistNames.size >= 4 || track.artist.trim() == "群星"

internal fun buildPlayerArtistAlbumText(track: Track?): androidx.compose.ui.text.AnnotatedString {
    val artist = track?.artist ?: "未知歌手"
    val album = track?.album ?: "未知专辑"
    return buildAnnotatedString {
        var cursor = 0
        val names = track?.artistNames.orEmpty()
        val isGroup = track?.let(::isPlayerArtistGroup) ?: false
        if (!isGroup && names.isNotEmpty()) {
            names.forEachIndexed { index, name ->
                if (name.isNotBlank()) {
                    val start = artist.indexOf(name, cursor)
                    if (start >= 0) {
                        append(artist.substring(cursor, start))
                        pushStringAnnotation("artist", index.toString()); append(name); pop()
                        cursor = start + name.length
                    }
                }
            }
        }
        if (cursor < artist.length) {
            pushStringAnnotation("artist", "0"); append(artist.substring(cursor)); pop()
        }
        append(" - ")
        pushStringAnnotation("album", "album"); append(album); pop()
    }
}

@Composable
internal fun PlayerProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = value.coerceIn(0f, 1f)
    var isDragging by remember { mutableStateOf(false) }
    val lineWidth by animateDpAsState(if (isDragging) 10.dp else 6.dp, label = "playerProgressWidth")
    BoxWithConstraints(modifier = modifier.height(24.dp).pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (kotlin.math.abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                        isDragging = true
                    }
                } while (change.pressed)
            } finally {
                isDragging = false
            }
        }
    }, contentAlignment = Alignment.CenterStart) {
        // 保留 Miuix 滑块的拖动、点击、触觉反馈和无障碍语义，视觉由细轨道单独绘制。
        Slider(
            value = progress,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            modifier = Modifier.requiredWidth(maxWidth + 18.dp).height(28.dp).alpha(0f)
        )
        ComposeCanvas(Modifier.matchParentSize()) {
            val strokeWidth = lineWidth.toPx()
            val radius = 5.dp.toPx()
            val left = radius
            val right = (size.width - radius).coerceAtLeast(left)
            val thumbX = if (rtl) right - (right - left) * progress else left + (right - left) * progress
            val y = size.height / 2f
            drawLine(color.copy(alpha = 0.3f), Offset(left, y), Offset(right, y), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(if (rtl) right else left, y), Offset(thumbX, y), strokeWidth, StrokeCap.Round)
            drawCircle(color, radius = radius, center = Offset(thumbX, y))
        }
    }
}

internal fun formatPlayerDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

internal fun getQualityText(
    bitrate: Int,
    actualQuality: String,
    isFMMode: Boolean = false,
    isIntelligenceMode: Boolean = false,
    showQuality: Boolean = true,
    fmModeLabel: String = "私人FM"
): String {
    val quality = actualQuality.ifBlank {
        if (bitrate > 0) com.hypermusic.miuix.data.model.AudioQuality.neteaseActual(bitrate, "") else "未知音质"
    }
    val prefix = when {
        isFMMode -> fmModeLabel
        isIntelligenceMode -> "心动模式"
        else -> ""
    }
    // 关闭「显示当前歌曲音质」时只保留私人FM/心动模式文字
    if (!showQuality) return prefix
    return if (prefix.isEmpty()) quality else "$prefix · $quality"
}

/** Render a shared cover bitmap without downloading it again for each layout. */
@Composable
internal fun CoverImage(
    bitmap: ImageBitmap?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Image(
            painter = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
internal fun FmLikeButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerDark = rememberPlayerDark()
    val playerText = if (playerDark) Color.White else Color.Black
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = if (isLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
            contentDescription = if (isLiked) "取消喜欢" else "喜欢",
            tint = if (isLiked) playerText else playerText.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun PlayerFavoriteButton(
    isLiked: Boolean,
    playerText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = if (isLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
            contentDescription = if (isLiked) "取消收藏" else "收藏",
            tint = if (isLiked) playerText else playerText.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun PlayerShareButton(
    playerText: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = MiuixIcons.Share,
            contentDescription = "分享",
            tint = playerText.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun PlayerCommentButton(
    playerText: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Comment,
            contentDescription = "评论",
            tint = playerText.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}
