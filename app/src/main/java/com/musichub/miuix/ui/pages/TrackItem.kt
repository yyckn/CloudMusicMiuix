package com.musichub.miuix.ui.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musichub.miuix.data.model.Track
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TrackItem(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicComponent(
        modifier = modifier.fillMaxWidth(),
        endActions = {
            Text(
                text = formatDuration(track.duration),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        onClick = onClick
    ) {
        Text(
            text = track.title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = if (isPlaying) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (track.isVip) {
                Text(
                    text = "VIP",
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "${track.artist} - ${track.album}",
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = if (isPlaying) MiuixTheme.colorScheme.primary.copy(alpha = 0.7f) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
