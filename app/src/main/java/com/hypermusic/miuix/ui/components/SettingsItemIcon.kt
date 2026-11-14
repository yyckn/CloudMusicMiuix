package com.hypermusic.miuix.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon

@Composable
fun SettingsItemIcon(@DrawableRes icon: Int) {
    Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
}
