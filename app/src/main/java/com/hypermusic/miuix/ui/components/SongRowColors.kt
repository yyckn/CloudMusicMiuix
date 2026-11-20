package com.hypermusic.miuix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Match settings cards for ordinary rows; reserve the primary container for the current song. */
@Composable
internal fun songRowBackground(isPlaying: Boolean): Color =
    if (isPlaying) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainer
