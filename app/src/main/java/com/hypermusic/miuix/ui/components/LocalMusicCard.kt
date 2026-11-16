package com.hypermusic.miuix.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.data.download.OfflineLibrary
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LocalMusicCard(onClick: () -> Unit) {
    val count = com.hypermusic.miuix.data.download.groupLocalDownloads(OfflineLibrary.available()).size
    if (count == 0) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        pressFeedbackType = PressFeedbackType.Sink, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(MiuixTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                Icon(MiuixIcons.Music, null, Modifier.size(28.dp), tint = MiuixTheme.colorScheme.primary)
            }
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
