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
    OverlayIconDropdownMenu(entry = DropdownEntry(items = listOf(
        DropdownItem(text = "网易云音乐", selected = platform == MusicPlatform.NETEASE,
            onClick = { PlatformManager.select(MusicPlatform.NETEASE) }),
        DropdownItem(text = "QQ音乐", selected = platform == MusicPlatform.QQ,
            onClick = { PlatformManager.select(MusicPlatform.QQ) })
    ))) {
        Box(modifier = Modifier.size(24.dp).clip(CircleShape)
            .background(if (platform == MusicPlatform.NETEASE) Color(0xFFE60026) else Color.White)) {
            Image(
                painter = painterResource(if (platform == MusicPlatform.NETEASE) R.drawable.ic_launcher_foreground else R.drawable.ic_qqmusic_classic),
                contentDescription = "切换音乐平台",
                modifier = Modifier.fillMaxSize().then(if (platform == MusicPlatform.NETEASE) Modifier.scale(1.5f) else Modifier)
            )
        }
    }
}
