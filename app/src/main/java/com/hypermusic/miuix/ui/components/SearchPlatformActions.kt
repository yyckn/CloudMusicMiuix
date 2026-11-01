package com.hypermusic.miuix.ui.components

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search

@Composable
fun SearchPlatformActions(onSearch: () -> Unit, showSearch: Boolean = true) {
    if (showSearch) IconButton(onClick = onSearch) {
        Icon(imageVector = MiuixIcons.Basic.Search, contentDescription = "搜索")
    }
    PlatformSwitch()
}
