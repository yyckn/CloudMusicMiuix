package com.hypermusic.miuix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.miuix.data.model.SongLyrics
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun LyricsView(
    lyrics: SongLyrics?,
    currentPosition: Long,
    onSeekTo: (Long) -> Unit,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    source: String? = null,
    sourceColor: Color = Color(0xFF9E9E9E),
    sourceStartPadding: Dp = 0.dp,
    showSourceHelp: Boolean = false,
    onSourceClick: (() -> Unit)? = null,
    positionProvider: (() -> Long)? = null,
    waitingForLyrics: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        AppleMusicLyricsView(
            waitingForLyrics = waitingForLyrics,
            lyrics = lyrics,
            currentPosition = currentPosition,
            onSeekTo = onSeekTo,
            isPlaying = isPlaying,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            positionProvider = positionProvider
        )
        if (!source.isNullOrBlank() && lyrics != null && lyrics.lines.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(start = sourceStartPadding, bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onSourceClick != null) Modifier.clickable { onSourceClick() } else Modifier)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSourceHelp) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = "自动升级歌词说明",
                        tint = sourceColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "歌词来自 $source",
                    fontSize = 12.sp,
                    color = sourceColor
                )
            }
        }
    }
}
