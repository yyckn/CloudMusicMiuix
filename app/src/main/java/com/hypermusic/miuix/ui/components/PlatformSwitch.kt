package com.hypermusic.miuix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.R
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.api.PlatformManager
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu

@Composable
fun PlatformSwitch() {
    val platform = PlatformManager.current
    OverlayIconDropdownMenu(entries = listOf(
        DropdownEntry(items = listOf(
        DropdownItem(text = "网易云音乐", selected = platform == MusicPlatform.NETEASE,
            icon = { PlatformLogo(MusicPlatform.NETEASE, it) },
            onClick = { PlatformManager.select(MusicPlatform.NETEASE) })
        )),
        DropdownEntry(items = listOf(
        DropdownItem(text = "QQ音乐", selected = platform == MusicPlatform.QQ,
            icon = { PlatformLogo(MusicPlatform.QQ, it) },
            onClick = { PlatformManager.select(MusicPlatform.QQ) }),
        DropdownItem(text = "波点音乐", summary = "QQ音乐简洁版", selected = platform == MusicPlatform.BODIAN,
            icon = { PlatformLogo(MusicPlatform.BODIAN, it) },
            onClick = { PlatformManager.select(MusicPlatform.BODIAN) }),
        DropdownItem(text = "酷狗音乐", selected = platform == MusicPlatform.KUGOU,
            icon = { PlatformLogo(MusicPlatform.KUGOU, it) },
            onClick = { PlatformManager.select(MusicPlatform.KUGOU) })
        )),
        DropdownEntry(items = listOf(
        DropdownItem(text = "汽水音乐", selected = platform == MusicPlatform.QISHUI,
            icon = { PlatformLogo(MusicPlatform.QISHUI, it) },
            onClick = { PlatformManager.select(MusicPlatform.QISHUI) })
        ))
    ), collapseOnSelection = true) {
        PlatformLogo(platform, contentDescription = "切换音乐平台")
    }
}

@Composable
private fun PlatformLogo(platform: MusicPlatform, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val background = when (platform) {
        MusicPlatform.NETEASE -> Color(0xFFE60026)
        MusicPlatform.KUGOU -> Color(0xFF0062FF)
        MusicPlatform.BODIAN -> Color(0xFF05E8B2)
        MusicPlatform.QISHUI -> Color.Black
        else -> Color.White
    }
    val drawable = when (platform) {
        MusicPlatform.NETEASE -> R.drawable.ic_launcher_foreground
        MusicPlatform.QQ -> R.drawable.ic_qqmusic_classic
        MusicPlatform.BODIAN -> R.drawable.ic_bodian_classic
        MusicPlatform.KUGOU -> R.drawable.ic_launcher_kugou_foreground
        MusicPlatform.QISHUI -> R.drawable.ic_qishui_classic
    }
    val foregroundScale = if (platform == MusicPlatform.NETEASE || platform == MusicPlatform.KUGOU) 1.5f else 1f
    Box(modifier = modifier.size(24.dp).clip(CircleShape).background(background)) {
        Image(painter = painterResource(drawable), contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize().scale(foregroundScale))
    }
}
