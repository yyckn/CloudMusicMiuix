package com.hypermusic.miuix.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import coil.compose.AsyncImage
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun QqProfilePage(onBackClick: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val accountId = QqMusicApi.dataAccountId
    val account = QqMusicApi.currentAccount()
    var profile by remember(accountId) { mutableStateOf<QqMusicApi.UserProfile?>(null) }
    var loading by remember(accountId) { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember(accountId) { mutableStateOf<String?>(null) }
    var showLogout by remember { mutableStateOf(false) }
    var showCredentialQr by remember { mutableStateOf(false) }
    var exportType by remember { mutableStateOf<String?>(null) }
    var exportCountdown by remember { mutableIntStateOf(3) }
    val scrollBehavior = MiuixScrollBehavior()
    val refreshState = rememberPullToRefreshState()
    val backdrop = rememberPageBlurBackdrop()
    val fileSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val result = runCatching {
                val credential = QqMusicApi.exportCredential() ?: error("账号已退出")
                val output = context.contentResolver.openOutputStream(uri) ?: error("无法打开文件")
                output.use { it.write(credential.toByteArray(Charsets.UTF_8)) }
            }
            Toast.makeText(context, if (result.isSuccess) "保存成功" else "保存失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
        }
    }
    BackHandler { onBackClick() }
    LaunchedEffect(accountId) {
        if (accountId != null) {
            QqMusicApi.getProfile().fold({ profile = it; error = null }, { error = it.message ?: "获取资料失败" })
        }
        loading = false
    }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            QqMusicApi.getProfile().fold({ profile = it; error = null }, { error = it.message ?: "刷新失败" })
            refreshing = false
        }
    }
    LaunchedEffect(exportType) {
        if (exportType != null) {
            exportCountdown = 3
            while (exportCountdown > 0) { delay(1000); exportCountdown-- }
        }
    }
    Scaffold(topBar = {
        BlurredBar(backdrop = backdrop) {
            TopAppBar(title = "我的资料", largeTitle = "我的资料", scrollBehavior = scrollBehavior,
                color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                        modifier = Modifier.padding(start = 8.dp).clickable { onBackClick() })
                },
                actions = {
                    OverlayIconDropdownMenu(entry = DropdownEntry(items = listOf(
                        DropdownItem(text = "二维码分享登录凭证", onClick = { showCredentialQr = true }),
                        DropdownItem(text = "复制登录凭证到剪贴板", onClick = { exportType = "clipboard" }),
                        DropdownItem(text = "将登录凭证导出为文件", onClick = { exportType = "file" })
                    ))) { Icon(imageVector = MiuixIcons.Share, contentDescription = "导出", tint = MiuixTheme.colorScheme.onSurface) }
                })
        }
    }) { padding ->
        PullToRefresh(isRefreshing = refreshing, onRefresh = { refreshing = true }, pullToRefreshState = refreshState,
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf("继续下拉以刷新", "松手后刷新", "正在刷新...", "刷新成功"),
            modifier = Modifier.padding(top = padding.calculateTopPadding()).pageLayerBackdrop(backdrop)) {
            LazyColumn(modifier = Modifier.fillMaxSize().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(bottom = FeedBottomPads.None), overscrollEffect = null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(model = profile?.avatarUrl ?: account?.avatarUrl, contentDescription = "头像",
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(40.dp)), contentScale = ContentScale.Crop,
                            error = painterResource(android.R.drawable.ic_menu_gallery))
                        Spacer(Modifier.height(12.dp))
                        Text(androidx.compose.ui.text.buildAnnotatedString {
                            append(profile?.name ?: account?.name.orEmpty())
                            account?.let {
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Normal))
                                append(if (it.credential.loginType == 1) "（微信登录）" else "（QQ登录）")
                                pop()
                            }
                        }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
                        if (profile?.isVip == true) {
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.clip(RoundedCornerShape(4.dp)).background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text(profile?.vipName ?: "VIP", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary)
                            }
                        }
                        if (loading) { Spacer(Modifier.height(8.dp)); InfiniteProgressIndicator() }
                        error?.let { Text(it, modifier = Modifier.padding(12.dp), color = MiuixTheme.colorScheme.error) }
                    }
                }
                item { SmallTitle("基本信息") }
                item {
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        BasicComponent(title = "用户ID", summary = profile?.userId ?: accountId.orEmpty())
                        BasicComponent(title = "会员状态", summary = when (profile?.isVip) {
                            true -> profile?.vipName ?: "VIP"
                            false -> "未开通"
                            null -> "暂不可用"
                        })
                        profile?.vipLevel?.takeIf { profile?.isVip == true }?.let { BasicComponent(title = "会员等级", summary = "Lv.$it") }
                        profile?.vipExpires?.takeIf { profile?.isVip == true }?.let { expiry ->
                            val seconds = expiry.toLongOrNull()
                            val formatted = if (seconds != null && seconds in 1_000_000_000L..9_999_999_999L)
                                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(seconds * 1000))
                            else expiry
                            BasicComponent(title = "会员到期时间", summary = formatted)
                        }
                    }
                }
                profile?.signature?.let { signature ->
                    item { SmallTitle("个人信息") }
                    item { Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        BasicComponent(title = "简介", summary = signature)
                    } }
                }
                item {
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showLogout = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error, contentColor = MiuixTheme.colorScheme.onError)) {
                        Text("退出登录", color = MiuixTheme.colorScheme.onError)
                    }
                }
            }
        }
        com.hypermusic.miuix.ui.scan.ShareCredentialQrSheet(showCredentialQr, "qq") { showCredentialQr = false }
        OverlayDialog(show = showLogout, title = "退出登录", summary = "确定要退出当前QQ音乐账号吗？",
            onDismissRequest = { showLogout = false }, content = {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(text = "取消", onClick = { showLogout = false }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(20.dp))
                    TextButton(text = "确定", onClick = { showLogout = false; onLogout() }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary())
                }
            })
        OverlayDialog(show = exportType != null, title = "警告",
            summary = "登录凭证可用于登录你的账号，请勿随意分享。导出后请妥善保管。",
            onDismissRequest = { exportType = null }, content = {
                Column {
                    TextButton(text = if (exportCountdown > 0) "已了解风险，继续导出（$exportCountdown）" else "已了解风险，继续导出",
                        enabled = exportCountdown == 0, onClick = {
                            val type = exportType
                            exportType = null
                            if (type == "file") fileSaver.launch("qqmusic-loginkey.json") else {
                                val credential = QqMusicApi.exportCredential()
                                if (credential != null) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("login_credentials", credential))
                                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
                    Spacer(Modifier.height(8.dp))
                    TextButton(text = "取消", onClick = { exportType = null }, modifier = Modifier.fillMaxWidth())
                }
            })
    }
}
