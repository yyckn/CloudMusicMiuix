// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
package top.yukonga.miuix.kmp.layout

import androidx.compose.animation.core.SpringSpec
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.anim.folmeSpring

object CascadingPopupDefaults {
    val PrimaryShrunkScale = .95f
    fun arrowRotation(layoutDirection: LayoutDirection): Float = if (layoutDirection == LayoutDirection.Ltr) -90f else 90f
    fun <T> expandSpring(expanding: Boolean): SpringSpec<T> = if (expanding) folmeSpring(.99f, .45f) else folmeSpring(.95f, .2f)
    fun <T> arrowSpring(expanding: Boolean): SpringSpec<T> = folmeSpring(.95f, if (expanding) .2f else .3f)
}
