package com.hypermusic.miuix.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.data.download.OfflineLibrary
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LocalMusicCard(onClick: () -> Unit) {
    val entries = OfflineLibrary.available()
    val count = com.hypermusic.miuix.data.download.groupLocalDownloads(entries).size
    val latest = remember(entries) { OfflineLibrary.latest(entries) }
    if (count == 0) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        pressFeedbackType = PressFeedbackType.Sink, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = latest?.let { OfflineLibrary.cover(it) ?: it.track.imgUrl }, contentDescription = "本地音乐封面",
                imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(androidx.compose.ui.platform.LocalContext.current,
                    com.hypermusic.miuix.data.cache.ImageCaches.Kind.THUMBNAIL),
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).sharedCover(playlistCoverKey("local_downloads")),
                contentScale = ContentScale.Crop)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("本地音乐", style = MiuixTheme.textStyles.main, fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("${count}首歌曲", style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}
