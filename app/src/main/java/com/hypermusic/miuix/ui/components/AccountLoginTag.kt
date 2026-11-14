package com.hypermusic.miuix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AccountLoginTag(label: String) {
    Box(Modifier.clip(RoundedCornerShape(4.dp))
        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
        .padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary)
    }
}
