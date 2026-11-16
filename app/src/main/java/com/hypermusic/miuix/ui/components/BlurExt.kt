package com.hypermusic.miuix.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.hypermusic.miuix.ui.theme.LocalEnableBlur

@Composable
fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** 页面标题栏模糊用的背景层（跟随全局模糊开关）。 */
@Composable
fun rememberPageBlurBackdrop(): LayerBackdrop? =
    if (top.yukonga.miuix.kmp.material.LocalSoftGlassEnabled.current) null
    else rememberBlurBackdrop(LocalEnableBlur.current)

/** 将页面内容记录到标题栏模糊背景层。 */
fun Modifier.pageLayerBackdrop(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

@Composable
fun BlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    isTopBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    // 柔光玻璃由标题栏组件统一绘制渐进模糊，避免再叠加旧的高浓度高斯底色。
    if (isTopBar && top.yukonga.miuix.kmp.material.LocalSoftGlassEnabled.current) {
        content()
        return
    }
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.87f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) {
        content()
    }
}
