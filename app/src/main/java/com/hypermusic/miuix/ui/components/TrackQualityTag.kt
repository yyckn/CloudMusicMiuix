package com.hypermusic.miuix.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Keep detailed quality names in settings and stored metadata; compact only list badges.
private val numericQualityDetail = Regex("[（(][^（）()]*\\d[^（）()]*[）)]")

@Composable
internal fun TrackQualityTag(quality: String) {
    Text(numericQualityDetail.replace(quality, "").trim(), fontSize = 10.sp, color = MiuixTheme.colorScheme.primary, maxLines = 1,
        modifier = Modifier.border(1.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp))
}

/** Shared third line beneath artist/album information in local and download rows. */
@Composable
internal fun TrackFileDetails(quality: String?, size: String?) {
    if (quality.isNullOrBlank() && size.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!quality.isNullOrBlank()) TrackQualityTag(quality)
        if (!size.isNullOrBlank()) Text(size, fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
    }
}
