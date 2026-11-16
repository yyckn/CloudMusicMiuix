package com.hypermusic.miuix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.material.LocalSoftGlassEnabled

/** Glass bars overlay the viewport; keep the initial inset inside the scrolling content. */
@Composable
internal fun pageViewportTopInset(top: Dp): Dp = if (LocalSoftGlassEnabled.current) 0.dp else top

@Composable
internal fun pageScrollTopInset(top: Dp): Dp = if (LocalSoftGlassEnabled.current) top else 0.dp

@Composable
internal fun pageNavigationIconInset(): Dp = if (LocalSoftGlassEnabled.current) 0.dp else 8.dp
