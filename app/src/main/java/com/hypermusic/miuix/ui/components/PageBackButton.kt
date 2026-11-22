package com.hypermusic.miuix.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The button owns the complete hit target and feedback; the arrow has no click handler. */
@Composable
internal fun PageBackButton(onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MiuixTheme.colorScheme.onSurface) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.size(24.dp), tint = tint)
    }
}
