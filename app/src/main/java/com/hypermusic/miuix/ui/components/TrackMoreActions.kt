package com.hypermusic.miuix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared top actions for song detail sheets. */
@Composable
fun TrackMoreActions(
    liked: Boolean,
    onShare: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    likeLabel: String = if (liked) "取消收藏" else "收藏",
    likeEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        @Composable
        fun Action(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                   onClick: () -> Unit, enabled: Boolean = true, highlighted: Boolean = false,
                   modifier: Modifier = Modifier) {
            Button(onClick = onClick, enabled = enabled, modifier = modifier,
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp),
                        tint = if (highlighted) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
                    Spacer(Modifier.height(6.dp))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
                }
            }
        }
        Action("分享", MiuixIcons.Share, onShare, modifier = Modifier.weight(1f))
        Action(likeLabel, if (liked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
            onLike, enabled = likeEnabled, highlighted = liked, modifier = Modifier.weight(1f))
        Action("评论", Icons.AutoMirrored.Filled.Comment, onComment, modifier = Modifier.weight(1f))
    }
}
