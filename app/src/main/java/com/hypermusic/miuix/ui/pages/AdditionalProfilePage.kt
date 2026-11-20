package com.hypermusic.miuix.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.hypermusic.miuix.data.api.*
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.*
import com.hypermusic.miuix.ui.navigation.BackHandler
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

/** Display only account information actually supplied by each platform, never session secrets. */
@Composable
internal fun AdditionalProfilePage(platform: MusicPlatform, selectedAccountId: String?, onBack: () -> Unit, onRemoved: () -> Unit) {
    val context = LocalContext.current
    val store = remember(platform) { AdditionalAccounts(context, platform) }
    val revision = AdditionalAccounts.revision.intValue
    val account = remember(platform, selectedAccountId, revision) { store.accounts().firstOrNull { it.id == (selectedAccountId ?: store.dataId) } }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val behavior = MiuixScrollBehavior()
    val backdrop = rememberPageBlurBackdrop()
    val platformName = when (platform) { MusicPlatform.BODIAN -> "波点音乐"; MusicPlatform.KUGOU -> "酷狗音乐"; else -> "汽水音乐" }
    BackHandler { onBack() }
    Scaffold(topBar = {
        BlurredBar(backdrop) {
            TopAppBar(title = "我的资料", largeTitle = "我的资料", scrollBehavior = behavior,
                color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                navigationIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    modifier = Modifier.padding(start = pageNavigationIconInset()).clickable(onClick = onBack)) })
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pageViewportTopInset(padding.calculateTopPadding()))
            .overScrollVertical().nestedScroll(behavior.nestedScrollConnection).pageLayerBackdrop(backdrop),
            contentPadding = PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding()), bottom = FeedBottomPads.None)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(account?.avatar, "头像", Modifier.size(80.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(12.dp))
                    Text(account?.nickname?.ifBlank { account.id } ?: "账号已退出", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    AccountLoginTag(platformName)
                }
            }
            if (account != null) {
                item { SmallTitle("基本信息") }
                item {
                    Card(Modifier.padding(horizontal = 12.dp)) {
                        BasicComponent(title = "用户ID", summary = account.id)
                        BasicComponent(title = "昵称", summary = account.nickname.ifBlank { account.id })
                        BasicComponent(title = "音乐平台", summary = platformName)
                        BasicComponent(title = "获取数据账号", summary = if (account.id == store.dataId) "当前使用" else "未使用")
                        val playback = if (platform == MusicPlatform.BODIAN) SharedPlaybackAccounts.current(context, "bodian")?.let {
                            it.source == "bodian" && it.id == account.id
                        } == true else account.id == store.playbackId
                        BasicComponent(title = "播放歌曲账号", summary = if (playback) "当前使用" else "未使用")
                        if (platform == MusicPlatform.QISHUI) {
                            val expires = remember(account) { runCatching { account.values["session"]?.let { QishuiSessionStore(context).decode(it).expiresAt } }.getOrNull() }
                            if (expires != null && expires > 0) BasicComponent(title = "登录凭据有效期", summary =
                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(if (expires < 10_000_000_000L) expires * 1000 else expires)))
                        }
                    }
                }
                item {
                    TextButton("退出账号", modifier = Modifier.fillMaxWidth().padding(12.dp), onClick = { confirm = true })
                }
            }
        }
    }
    OverlayDialog(show = confirm, title = "退出账号", summary = "确定退出此${platformName}账号吗？", onDismissRequest = {}) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", modifier = Modifier.fillMaxWidth(), onClick = { confirm = false })
            TextButton("退出账号", modifier = Modifier.fillMaxWidth(), onClick = {
                runCatching { account?.let { store.remove(it.id) } }.onSuccess { confirm = false; onRemoved() }
                    .onFailure { error = it.message ?: "退出账号失败"; confirm = false }
            })
        }
    }
    OverlayDialog(show = error != null, title = "账号操作失败", summary = error.orEmpty(), onDismissRequest = { error = null }) {
        TextButton("确定", modifier = Modifier.fillMaxWidth(), onClick = { error = null })
    }
}
