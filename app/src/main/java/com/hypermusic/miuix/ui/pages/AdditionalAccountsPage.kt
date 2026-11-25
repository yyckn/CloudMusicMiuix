package com.hypermusic.miuix.ui.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hypermusic.miuix.data.api.AdditionalAccounts
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.api.SharedPlaybackAccounts
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
internal fun AdditionalAccountsPage(platform: MusicPlatform, onBack: () -> Unit, onLogin: () -> Unit, onChanged: () -> Unit,
    onProfile: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember(platform) { AdditionalAccounts(context, platform) }
    val revision = AdditionalAccounts.revision.intValue
    val accounts = remember(platform, revision) { store.accounts() }
    val routingRevision = SharedPlaybackAccounts.revision.intValue
    val playbackOptions = if (platform == MusicPlatform.BODIAN) SharedPlaybackAccounts.options(context, "bodian") else null
    val playbackSelection = if (platform == MusicPlatform.BODIAN) SharedPlaybackAccounts.current(context, "bodian") else null
    var error by remember { mutableStateOf<String?>(null) }
    fun action(block: () -> Unit) { runCatching(block).onSuccess { onChanged() }.onFailure { error = it.message ?: "账号操作失败" } }
    ManageAccountsPage(accounts = accounts.map { AccountInfo(it.id, it.nickname.ifBlank { it.id }, it.avatar) },
        onBackClick = onBack, onAccountClick = onProfile, onLoginNewAccount = onLogin,
        onAccountSettingsChanged = onChanged,
        activeAccountId = store.dataId,
        playbackAccountIndex = playbackOptions?.indexOfFirst { it.selection == playbackSelection }?.coerceAtLeast(0)
            ?: accounts.indexOfFirst { it.id == store.playbackId }.coerceAtLeast(0),
        dataAccountIndex = accounts.indexOfFirst { it.id == store.dataId }.coerceAtLeast(0),
        onPlaybackAccountChange = { index ->
            if (playbackOptions != null) playbackOptions.getOrNull(index)?.let { action { SharedPlaybackAccounts.select(context, "bodian", it.selection) } }
            else accounts.getOrNull(index)?.let { action { store.selectPlayback(it.id) } }
        },
        onDataAccountChange = { index -> accounts.getOrNull(index)?.let { action { store.selectData(it.id) } } },
        playbackOptions = playbackOptions?.map { it.label }, playbackInterop = platform == MusicPlatform.BODIAN)
    OverlayDialog(show = error != null, title = "账号操作失败", summary = error.orEmpty(), onDismissRequest = { error = null }) {
        TextButton(text = "确定", onClick = { error = null }, modifier = Modifier.fillMaxWidth())
    }
}
