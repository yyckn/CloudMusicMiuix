package com.musichub.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musichub.miuix.R
import com.musichub.miuix.ui.FeedBottomPads
import com.musichub.miuix.ui.components.BlurredBar
import com.musichub.miuix.ui.components.pageLayerBackdrop
import com.musichub.miuix.ui.components.rememberPageBlurBackdrop
import com.musichub.miuix.ui.theme.AppIconSettings
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun AppIconPage(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberPageBlurBackdrop()
    val context = LocalContext.current
    val current = AppIconSettings.current
    var pendingIcon by remember { mutableStateOf<AppIconSettings.AppIcon?>(null) }
    val requestIconChange: (AppIconSettings.AppIcon) -> Unit = { newIcon ->
        if (current != newIcon) {
            pendingIcon = newIcon
        }
    }

    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "应用图标",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { onBackClick() }
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pageLayerBackdrop(blurBackdrop),
            contentPadding = PaddingValues(bottom = FeedBottomPads.None)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item { SmallTitle("当前图标") }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AppIconPreview(
                        icon = current,
                        size = 120.dp,
                        corner = 28.dp
                    )
                }
            }

            item { SmallTitle("莫奈取色系列") }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        IconChoice(
                            icon = AppIconSettings.AppIcon.NOTES,
                            label = "音符",
                            selected = current == AppIconSettings.AppIcon.NOTES,
                            onClick = {
                                requestIconChange(AppIconSettings.AppIcon.NOTES)
                            }
                        )
                    }
                    item {
                        IconChoice(
                            icon = AppIconSettings.AppIcon.NETEASE,
                            label = "网易云",
                            selected = current == AppIconSettings.AppIcon.NETEASE,
                            onClick = {
                                requestIconChange(AppIconSettings.AppIcon.NETEASE)
                            }
                        )
                    }
                    item {
                        IconChoice(
                            icon = AppIconSettings.AppIcon.KUGOU,
                            label = "酷狗",
                            selected = current == AppIconSettings.AppIcon.KUGOU,
                            onClick = {
                                requestIconChange(AppIconSettings.AppIcon.KUGOU)
                            }
                        )
                    }
                }
            }

            item { SmallTitle("经典图标系列") }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        IconChoice(
                            icon = AppIconSettings.AppIcon.KUGOU_CLASSIC,
                            label = "酷狗音乐",
                            selected = current == AppIconSettings.AppIcon.KUGOU_CLASSIC,
                            onClick = {
                                requestIconChange(AppIconSettings.AppIcon.KUGOU_CLASSIC)
                            }
                        )
                    }
                    item {
                        IconChoice(
                            icon = AppIconSettings.AppIcon.NETEASE_CLASSIC,
                            label = "网易云音乐",
                            selected = current == AppIconSettings.AppIcon.NETEASE_CLASSIC,
                            onClick = {
                                requestIconChange(AppIconSettings.AppIcon.NETEASE_CLASSIC)
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        OverlayDialog(
            show = pendingIcon != null,
            title = "切换图标",
            summary = "切换图标需要重启APP后生效，是否继续？",
            onDismissRequest = { pendingIcon = null },
            content = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { pendingIcon = null },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(
                        text = "继续",
                        onClick = {
                            val icon = pendingIcon
                            if (icon != null) {
                                AppIconSettings.select(context, icon)
                                android.widget.Toast.makeText(
                                    context,
                                    "图标已切换，重启APP后生效",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            pendingIcon = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        )
    }
}

@Composable
private fun IconChoice(
    icon: AppIconSettings.AppIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(24.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            AppIconPreview(
                icon = icon,
                size = if (selected) 78.dp else 84.dp,
                corner = if (selected) 18.dp else 20.dp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (selected) "$label（当前）" else label,
            style = MiuixTheme.textStyles.body2,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun AppIconPreview(
    icon: AppIconSettings.AppIcon,
    size: Dp,
    corner: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
    ) {
        when (icon) {
            AppIconSettings.AppIcon.NOTES,
            AppIconSettings.AppIcon.NETEASE,
            AppIconSettings.AppIcon.KUGOU -> {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            AppIconSettings.AppIcon.KUGOU_CLASSIC -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0062FF))
                )
            }
            AppIconSettings.AppIcon.NETEASE_CLASSIC -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE60026))
                )
            }
        }

        val foreground = when (icon) {
            AppIconSettings.AppIcon.NOTES -> R.drawable.ic_music_note
            AppIconSettings.AppIcon.NETEASE,
            AppIconSettings.AppIcon.NETEASE_CLASSIC -> R.drawable.ic_launcher_foreground
            AppIconSettings.AppIcon.KUGOU,
            AppIconSettings.AppIcon.KUGOU_CLASSIC -> R.drawable.ic_launcher_kugou_foreground
        }
        val foregroundFraction = if (icon == AppIconSettings.AppIcon.NOTES) 0.66f else 1.5f
        Image(
            painter = painterResource(foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(foregroundFraction)
                .align(Alignment.Center),
            contentScale = ContentScale.Fit
        )
    }
}
