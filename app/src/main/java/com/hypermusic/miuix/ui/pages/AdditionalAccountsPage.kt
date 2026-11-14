package com.hypermusic.miuix.ui.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hypermusic.miuix.data.api.AdditionalAccounts
import com.hypermusic.miuix.data.api.MusicPlatform
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
internal fun AdditionalAccountsPage(platform: MusicPlatform, onBack: () -> Unit, onLogin: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val store = remember(platform) { AdditionalAccounts(context, platform) }
    val revision = AdditionalAccounts.revision.intValue
    val accounts = remember(platform, revision) { store.accounts() }
    var selected by remember(platform) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    fun action(block: () -> Unit) { runCatching(block).onSuccess { onChanged() }.onFailure { error = it.message ?: "账号操作失败" } }
    ManageAccountsPage(accounts = accounts.map { AccountInfo(it.id, it.nickname.ifBlank { it.id }, it.avatar) },
        onBackClick = onBack, onAccountClick = { selected = it }, onLoginNewAccount = onLogin,
        activeAccountId = store.dataId,
        playbackAccountIndex = accounts.indexOfFirst { it.id == store.playbackId }.coerceAtLeast(0),
        dataAccountIndex = accounts.indexOfFirst { it.id == store.dataId }.coerceAtLeast(0),
        onPlaybackAccountChange = { index -> accounts.getOrNull(index)?.let { action { store.selectPlayback(it.id) } } },
        onDataAccountChange = { index -> accounts.getOrNull(index)?.let { action { store.selectData(it.id) } } })
    OverlayDialog(show = selected != null, title = "管理账号", summary = accounts.firstOrNull { it.id == selected }?.nickname.orEmpty(),
        onDismissRequest = { selected = null }) {
        TextButton(text = "删除账号", onClick = { deleting = selected; selected = null }, modifier = Modifier.fillMaxWidth())
        TextButton(text = "取消", onClick = { selected = null }, modifier = Modifier.fillMaxWidth())
    }
    OverlayDialog(show = deleting != null, title = "删除账号", summary = "确定删除此账号的登录信息吗？",
        onDismissRequest = { deleting = null }) {
        TextButton(text = "取消", onClick = { deleting = null }, modifier = Modifier.fillMaxWidth())
        TextButton(text = "删除", onClick = { deleting?.let { action { store.remove(it) } }; deleting = null }, modifier = Modifier.fillMaxWidth())
    }
    OverlayDialog(show = error != null, title = "账号操作失败", summary = error.orEmpty(), onDismissRequest = { error = null }) {
        TextButton(text = "确定", onClick = { error = null }, modifier = Modifier.fillMaxWidth())
    }
}
