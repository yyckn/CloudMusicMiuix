package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.PageBackButton

import com.hypermusic.miuix.ui.navigation.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.components.AsyncImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.data.api.AdditionalAccounts
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.api.PlatformManager
import com.hypermusic.miuix.data.api.SharedPlaybackAccounts
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

data class AccountInfo(
    val userId: String,
    val nickname: String,
    val avatarUrl: String,
    val loginLabel: String? = null
)

@Composable
fun ManageAccountsPage(
    accounts: List<AccountInfo>,
    onBackClick: () -> Unit,
    onAccountClick: (String) -> Unit,
    onLoginNewAccount: () -> Unit,
    activeAccountId: String? = null,
    playbackAccountIndex: Int = 0,
    dataAccountIndex: Int = 0,
    onPlaybackAccountChange: (Int) -> Unit = {},
    onDataAccountChange: (Int) -> Unit = {},
    playbackOptions: List<String>? = null,
    playbackInterop: Boolean = false,
    modifier: Modifier = Modifier,
    onAccountSettingsChanged: () -> Unit = {}
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val platform = PlatformManager.current
    val extraStore = remember(platform) {
        if (platform in setOf(MusicPlatform.BODIAN, MusicPlatform.KUGOU, MusicPlatform.QISHUI)) AdditionalAccounts(context, platform) else null
    }
    val prefs = remember(platform) { context.getSharedPreferences(if (platform == MusicPlatform.QQ) "qqmusic_prefs" else "netease_prefs", android.content.Context.MODE_PRIVATE) }
    var nativeMultiEnabled by remember(platform) { mutableStateOf(prefs.getBoolean("multi_account_enabled", false)) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { store, key ->
            if (key == "multi_account_enabled") nativeMultiEnabled = store.getBoolean(key, false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val extraRevision = AdditionalAccounts.revision.intValue
    val multiEnabled = if (extraStore != null) { extraRevision; extraStore.enabled } else nativeMultiEnabled
    var accountError by remember { mutableStateOf<String?>(null) }
    val currentAccountCount by rememberUpdatedState(accounts.size)
    val currentSettingsChanged by rememberUpdatedState(onAccountSettingsChanged)
    fun changeMultiAccount(enabled: Boolean) {
        if (!enabled && currentAccountCount > 1) {
            accountError = "当前账号数量大于1，需要删除部分账号才能关闭多账号功能。"
            return
        }
        runCatching {
            if (extraStore != null) extraStore.enabled = enabled
            else {
                prefs.edit().putBoolean("multi_account_enabled", enabled).apply()
                nativeMultiEnabled = enabled
            }
            SharedPlaybackAccounts.revision.intValue++
            currentSettingsChanged()
        }.onFailure { accountError = it.message ?: "账号设置保存失败" }
    }

    
    // 构建账号显示列表
    val accountOptions = accountOptionLabels(accounts)

    BackHandler {
        onBackClick()
    }

    val blurBackdrop = rememberPageBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "管理账号",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = { PageBackButton(onClick = { onBackClick() }) }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(top = paddingValues.calculateTopPadding())
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pageLayerBackdrop(blurBackdrop),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = FeedBottomPads.None)
        ) {
            // 账号管理
            item { SmallTitle("账号管理", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) }
            item {
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    SwitchPreference(title = "多账号切换", checked = multiEnabled,
                        startAction = { Icon(MiuixIcons.Contacts, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                        onCheckedChange = ::changeMultiAccount)
                }
            }
            
            // 账号列表
            accounts.forEach { account ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = { onAccountClick(account.userId) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = account.avatarUrl,
                                contentDescription = account.nickname,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(24.dp)),
                                contentScale = ContentScale.Crop,
                                error = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter()
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = account.nickname,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface
                                    )
                                    account.loginLabel?.let { com.hypermusic.miuix.ui.components.AccountLoginTag(it) }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ID: ${account.userId}",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
            
            // 登录新账号按钮
            if (accounts.isEmpty() || multiEnabled) item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onLoginNewAccount,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(text = "登录新账号")
                }
            }
            
            // Preserve the existing count rule, including QQ/Bodian playback interoperability.
            if (maxOf(accounts.size, playbackOptions?.size ?: 0) >= 2) {
                item {
                    SmallTitle("账号切换", insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                }
                item {
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        WindowDropdownPreference(title = "播放歌曲", summary = "需登录带有VIP账号后才能播放部分版权歌曲",
                            items = playbackOptions ?: accountOptions, selectedIndex = playbackAccountIndex,
                            enabled = multiEnabled, onSelectedIndexChange = onPlaybackAccountChange)
                        if (accounts.isNotEmpty()) WindowDropdownPreference(title = "获取数据", summary = "个人歌单、每日推荐、收藏等",
                            items = accountOptions, selectedIndex = dataAccountIndex,
                            enabled = multiEnabled, onSelectedIndexChange = onDataAccountChange)
                    }
                }
                if (playbackInterop) item {
                    Text("*QQ音乐和波点音乐支持播放歌曲账号互通", style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
                }
            }
        }
    }
    OverlayDialog(show = accountError != null, title = "无法关闭多账号", summary = accountError.orEmpty(),
        onDismissRequest = { accountError = null }) {
        TextButton("确定", onClick = { accountError = null }, modifier = Modifier.fillMaxWidth())
    }
}
