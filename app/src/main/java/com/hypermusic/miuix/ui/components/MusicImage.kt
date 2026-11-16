package com.hypermusic.miuix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.ImageLoader
import coil.compose.AsyncImagePainter
import coil.imageLoader
import com.hypermusic.miuix.R
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A size-independent fallback shared by remote images and the player's shared bitmap. */
@Composable
internal fun rememberMusicImageFallbackPainter(): Painter {
    val background = MiuixTheme.colorScheme.surfaceContainer
    val tint = (if (MiuixTheme.colorScheme.surface.luminance() < .5f) Color.White else Color.Black).copy(alpha = .25f)
    val note = painterResource(R.drawable.ic_music_note)
    return remember(background, tint, note) { MusicImageFallbackPainter(background, tint, note) }
}

private class MusicImageFallbackPainter(val background: Color, val tint: Color, val note: Painter) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified
    override fun DrawScope.onDraw() {
        drawRect(background)
        val side = size.minDimension * .66f
        translate((size.width - side) / 2f, (size.height - side) / 2f) {
            with(note) { draw(Size(side, side), colorFilter = ColorFilter.tint(tint)) }
        }
    }
}

/** Preserve Coil's loading/cache behavior while giving every failed or missing image the same fallback. */
@Composable
internal fun AsyncImage(
    model: Any?, contentDescription: String?, modifier: Modifier = Modifier,
    imageLoader: ImageLoader = LocalContext.current.imageLoader,
    placeholder: Painter? = null, error: Painter? = null, fallback: Painter? = null,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center, contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f, colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = FilterQuality.Low, clipToBounds: Boolean = true
) {
    val failed = rememberMusicImageFallbackPainter()
    val localModel = (model as? String)?.let { com.hypermusic.miuix.data.download.OfflineLibrary.coverForUrl(it) }
    coil.compose.AsyncImage(model = localModel ?: model, contentDescription = contentDescription, modifier = modifier,
        imageLoader = imageLoader, placeholder = placeholder, error = error ?: failed, fallback = fallback ?: error ?: failed,
        onLoading = onLoading, onSuccess = onSuccess, onError = onError,
        alignment = alignment, contentScale = contentScale, alpha = alpha, colorFilter = colorFilter,
        filterQuality = filterQuality, clipToBounds = clipToBounds)
}
