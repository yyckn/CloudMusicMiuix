package com.hypermusic.miuix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.material.LocalSoftGlassEnabled
import com.hypermusic.miuix.ui.theme.LocalEnableBlur

/** Both blur styles need content behind the entire bar, including the status-bar area.
 * Put the initial spacing inside the scrollable content instead of clipping its viewport
 * below the app bar. Opaque Gaussian bars can still use the reduced viewport.
 */
@Composable
internal fun pageViewportTopInset(top: Dp): Dp =
    if (LocalSoftGlassEnabled.current || LocalEnableBlur.current) 0.dp else top

@Composable
internal fun pageScrollTopInset(top: Dp): Dp =
    if (LocalSoftGlassEnabled.current || LocalEnableBlur.current) top else 0.dp

@Composable
internal fun pageNavigationIconInset(): Dp = if (LocalSoftGlassEnabled.current) 0.dp else 8.dp
