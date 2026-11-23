package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.R
import com.hypermusic.miuix.ui.components.SettingsItemIcon
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.components.AsyncImage
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.model.UserProfile
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.hypermusic.miuix.ui.FeedBottomPads
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.File


@Composable
fun SettingsPage(
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    onThemeSettingsClick: () -> Unit,
    onAppIconClick: () -> Unit = {},
    onPlayPageSettingsClick: () -> Unit = {},
    onSongSettingsClick: () -> Unit = {},
    onCacheSettingsClick: () -> Unit = {},
    onDownloadSettingsClick: () -> Unit = {},
    onAboutClick: () -> Unit,
    onLyriconAdapterClick: () -> Unit = {},
    onLyricSettingsClick: () -> Unit = {},
    onManageAccountsClick: () -> Unit = {},
    onListeningHistoryClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    showSearchAction: Boolean = true,
    profileVersion: Int = 0,
    onExtraAccountChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val platform = com.hypermusic.miuix.data.api.PlatformManager.current
    val isQq = platform == com.hypermusic.miuix.data.api.MusicPlatform.QQ
    val isNetease = platform == com.hypermusic.miuix.data.api.MusicPlatform.NETEASE
    val qqName = com.hypermusic.miuix.data.api.QqMusicApi.accountName
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val additionalRevision = com.hypermusic.miuix.data.api.AdditionalAccounts.revision.intValue
    val extraStore = remember(platform) { if (platform in setOf(com.hypermusic.miuix.data.api.MusicPlatform.BODIAN, com.hypermusic.miuix.data.api.MusicPlatform.KUGOU, com.hypermusic.miuix.data.api.MusicPlatform.QISHUI)) com.hypermusic.miuix.data.api.AdditionalAccounts(context, platform) else null }
    val extraMultiEnabled = remember(platform, additionalRevision) { extraStore?.enabled == true }
    val extraAccount = remember(platform, profileVersion, additionalRevision) {
        runCatching {
            when (platform) {
                com.hypermusic.miuix.data.api.MusicPlatform.BODIAN -> {
                    val api = com.hypermusic.miuix.data.api.BodianClient(context)
                    com.hypermusic.miuix.data.api.BodianAuth(context, api).currentAccount()?.let { Triple(it.nickname.ifBlank { it.uid }, it.uid, it.avatar) }
                }
                com.hypermusic.miuix.data.api.MusicPlatform.KUGOU -> {
                    val api = com.hypermusic.miuix.data.api.KugouClient(context)
                    com.hypermusic.miuix.data.api.KugouAuth(context, api).account()?.let { Triple(it.nickname.ifBlank { "酷狗音乐账号" }, it.userId, it.avatar) }
                }
                com.hypermusic.miuix.data.api.MusicPlatform.QISHUI -> {
                    val api = com.hypermusic.miuix.data.api.QishuiClient(context)
                    com.hypermusic.miuix.data.api.QishuiAuth(context, api).account()?.let { Triple(it.nickname.ifBlank { it.userId }, it.userId, it.avatar) }
                }
                else -> null
            }
        }
    }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isMultiAccountEnabled by remember { 
        mutableStateOf(
            context.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("multi_account_enabled", false)
        ) 
    }
    var isQqMultiAccountEnabled by remember {
        mutableStateOf(context.getSharedPreferences("qqmusic_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean("multi_account_enabled", false))
    }
    DisposableEffect(context) {
        val neteasePrefs = context.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
        val qqPrefs = context.getSharedPreferences("qqmusic_prefs", android.content.Context.MODE_PRIVATE)
        val neteaseListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "multi_account_enabled") isMultiAccountEnabled = prefs.getBoolean(key, false)
        }
        val qqListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "multi_account_enabled") isQqMultiAccountEnabled = prefs.getBoolean(key, false)
        }
        neteasePrefs.registerOnSharedPreferenceChangeListener(neteaseListener)
        qqPrefs.registerOnSharedPreferenceChangeListener(qqListener)
        onDispose {
            neteasePrefs.unregisterOnSharedPreferenceChangeListener(neteaseListener)
            qqPrefs.unregisterOnSharedPreferenceChangeListener(qqListener)
        }
    }
    var showAccountLimitDialog by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    
    // 获取APP版本号
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    // 加载用户信息
    LaunchedEffect(isLoggedIn, platform, profileVersion) {
        if (!isNetease || !isLoggedIn) userProfile = null
        if (isLoggedIn && isNetease) {
            // 先从缓存加载
            val cachedProfile = NeteaseApi.getUserProfileFromCache()
            if (cachedProfile != null) {
                userProfile = cachedProfile
            } else {
                // 缓存没有才请求API
                NeteaseApi.getUserProfile().fold(
                    onSuccess = { profile ->
                        userProfile = profile
                    },
                    onFailure = {
                        // Ignore
                    }
                )
            }
        }
    }

    // 下拉刷新回调
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && isQq && com.hypermusic.miuix.data.api.QqMusicApi.isLoggedIn()) {
            com.hypermusic.miuix.data.api.QqMusicApi.refreshProfile().onFailure {
                android.widget.Toast.makeText(context, it.message ?: "刷新QQ账号失败", android.widget.Toast.LENGTH_SHORT).show()
            }
            isRefreshing = false
        } else if (isRefreshing && isLoggedIn && isNetease) {
            NeteaseApi.getUserProfile().fold(
                onSuccess = { profile ->
                    userProfile = profile
                },
                onFailure = {
                    // Ignore
                }
            )
            isRefreshing = false
        } else {
            isRefreshing = false
        }
    }

    val blurBackdrop = rememberPageBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    actions = { com.hypermusic.miuix.ui.components.SearchPlatformActions(onSearchClick, showSearchAction) },
                    title = "设置",
                    largeTitle = "设置",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false
                )
            }
        }
    ) { paddingValues ->
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            pullToRefreshState = pullToRefreshState,
            contentPadding = PaddingValues(top = pageScrollTopInset(paddingValues.calculateTopPadding())),
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf(
                "继续下拉以刷新",
                "松手后刷新",
                "正在刷新...",
                "刷新成功"
            ),
            modifier = Modifier
                .padding(top = pageViewportTopInset(paddingValues.calculateTopPadding()))
                .pageLayerBackdrop(blurBackdrop)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(top = pageScrollTopInset(paddingValues.calculateTopPadding()), bottom = FeedBottomPads.More),
                overscrollEffect = null
            ) {
                item { Spacer(modifier = Modifier.size(12.dp)) }
                item { SmallTitle("账号") }
                if (!isQq && !isNetease) {
                    item {
                        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            val account = extraAccount.getOrNull()
                            if (!extraMultiEnabled || account == null) BasicComponent(
                                title = if (extraAccount.isFailure) "无法读取账号" else account?.first ?: "点击登录",
                                summary = if (extraAccount.isFailure) extraAccount.exceptionOrNull()?.message ?: "请重试" else if (account == null) "登录后享受完整音乐体验" else "账号：${account.second}",
                                startAction = {
                                    if (!account?.third.isNullOrBlank()) {
                                        AsyncImage(model = account?.third, contentDescription = "头像", modifier = Modifier.size(48.dp).clip(CircleShape))
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                },
                                onClick = { if (account == null && extraAccount.isSuccess) onLoginClick() else if (account != null) onProfileClick() }
                            )
                            if (account != null || extraMultiEnabled) {
                                SwitchPreference(checked = extraMultiEnabled, title = "多账号切换",
                                    summary = if (extraMultiEnabled) "已开启" else "未开启",
                                    startAction = { Icon(MiuixIcons.Contacts, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                                    onCheckedChange = { enabled ->
                                        if (!enabled && (extraStore?.accounts()?.size ?: 0) > 1) showAccountLimitDialog = true
                                        else extraStore?.enabled = enabled
                                    })
                                if (extraMultiEnabled) ArrowPreference(title = "管理多账号", summary = "添加、切换和管理账号",
                                    startAction = { Icon(MiuixIcons.ConvertFile, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                                    onClick = onManageAccountsClick)
                            }
                        }
                    }
                } else if (isQq) {
                    item {
                        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            if (qqName == null || !isQqMultiAccountEnabled) {
                                val avatar = com.hypermusic.miuix.data.api.QqMusicApi.currentAccount()?.avatarUrl.orEmpty()
                                if (qqName == null) {
                                    BasicComponent(title = "点击登录", summary = "登录后享受完整音乐体验", onClick = onLoginClick)
                                } else {
                                    val account = com.hypermusic.miuix.data.api.QqMusicApi.currentAccount()
                                    BasicComponent(
                                        startAction = {
                                            if (avatar.isNotBlank()) {
                                                AsyncImage(model = avatar, contentDescription = "头像",
                                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)),
                                                    contentScale = ContentScale.Crop,
                                                    error = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter())
                                                Spacer(modifier = Modifier.width(16.dp))
                                            }
                                        },
                                        onClick = onProfileClick
                                    ) {
                                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(qqName, fontSize = MiuixTheme.textStyles.headline1.fontSize,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                                            account?.let { com.hypermusic.miuix.ui.components.AccountLoginTag(
                                                if (it.credential.loginType == 1) "微信登录" else "QQ登录") }
                                        }
                                        Text(text = "查看个人资料", color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            style = MiuixTheme.textStyles.footnote1)
                                    }
                                }
                            }
                            if (qqName != null) {
                                SwitchPreference(
                                    checked = isQqMultiAccountEnabled,
                                    onCheckedChange = {
                                        if (!it && com.hypermusic.miuix.data.api.QqMusicApi.accounts.size > 1) { showAccountLimitDialog = true; return@SwitchPreference }
                                        isQqMultiAccountEnabled = it
                                        context.getSharedPreferences("qqmusic_prefs", android.content.Context.MODE_PRIVATE)
                                            .edit().putBoolean("multi_account_enabled", it).apply()
                                    },
                                    title = "多账号切换", startAction = { Icon(MiuixIcons.Contacts, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                                    summary = if (isQqMultiAccountEnabled) "已开启" else "未开启"
                                )
                                if (isQqMultiAccountEnabled) ArrowPreference(
                                    title = "管理多账号", startAction = { Icon(MiuixIcons.ConvertFile, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) }, summary = "添加、切换和管理账号", onClick = onManageAccountsClick
                                )
                            }
                        }
                    }
                } else {
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        onClick = if (isLoggedIn) onProfileClick else onLoginClick
                    ) {
                        // 未登录状态 或 已登录但未开启多账号
                        if (!isLoggedIn || !isMultiAccountEnabled) {
                            BasicComponent(
                                title = if (isLoggedIn) userProfile?.nickname ?: "已登录" else "点击登录",
                                summary = if (isLoggedIn) "查看个人资料" else "登录后享受完整音乐体验",
                                startAction = {
                                    if (isLoggedIn && userProfile?.avatarUrl?.isNotEmpty() == true) {
                                        AsyncImage(
                                            model = userProfile?.avatarUrl,
                                            contentDescription = "头像",
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(24.dp)),
                                            contentScale = ContentScale.Crop,
                                            error = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter()
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                },
                                onClick = if (isLoggedIn) onProfileClick else onLoginClick
                            )
                        }
                        
                        // 已登录状态下显示多账号开关
                        if (isLoggedIn) {
                            SwitchPreference(
                                checked = isMultiAccountEnabled,
                                onCheckedChange = { 
                                    if (!it && NeteaseApi.getAccounts().size > 1) { showAccountLimitDialog = true; return@SwitchPreference }
                                    isMultiAccountEnabled = it
                                    // 保存状态
                                    context.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("multi_account_enabled", it)
                                        .apply()
                                },
                                title = "多账号切换", startAction = { Icon(MiuixIcons.Contacts, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                                summary = if (isMultiAccountEnabled) "已开启" else "未开启"
                            )
                        }
                        
                        // 多账号开启后显示管理选项
                        if (isLoggedIn && isMultiAccountEnabled) {
                            ArrowPreference(
                                title = "管理多账号", startAction = { Icon(MiuixIcons.ConvertFile, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                                summary = "添加、切换和管理账号",
                                onClick = onManageAccountsClick
                            )
                        }
                    }
                }
                }
                if (isNetease) item {
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        ArrowPreference(title = "听歌记录", summary = "播放历史与听歌足迹", onClick = onListeningHistoryClick,
                            startAction = { Icon(MiuixIcons.Playlist, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) })
                    }
                }
                item { SmallTitle("个性化") }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        ArrowPreference(
                            title = "主题设置", startAction = { Icon(MiuixIcons.Sidebar, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                            summary = "主题色、底栏等",
                            onClick = onThemeSettingsClick
                        )
                        ArrowPreference(
                            title = "应用图标和名称", startAction = { SettingsItemIcon(R.drawable.ic_md3_broken_image); Spacer(Modifier.width(12.dp)) },
                            onClick = onAppIconClick
                        )
                        ArrowPreference(
                            title = "播放页设置", startAction = { Icon(MiuixIcons.Tune, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                            summary = "歌词、快捷键等",
                            onClick = onPlayPageSettingsClick
                        )
                    }
                }
                item { SmallTitle("播放") }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        ArrowPreference(
                            title = "音频设置", startAction = { Icon(MiuixIcons.Music, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                            summary = "音质、播放速度等",
                            onClick = onSongSettingsClick
                        )
                        ArrowPreference(
                            title = "歌词设置", startAction = { Icon(MiuixIcons.Playlist, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                            summary = "歌词来源、逐字歌词等",
                            onClick = onLyricSettingsClick
                        )
                        ArrowPreference(
                            title = "歌词接口适配",
                            startAction = { Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                            onClick = onLyriconAdapterClick
                        )
                    }
                }
                item { SmallTitle("缓存和下载") }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        ArrowPreference(title = "下载", startAction = { Icon(MiuixIcons.Download, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) }, onClick = onDownloadSettingsClick)
                        ArrowPreference(title = "缓存", startAction = { Icon(MiuixIcons.File, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) }, onClick = onCacheSettingsClick)
                    }
                }
                item { SmallTitle("其他") }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        ArrowPreference(
                            title = "关于", startAction = { Icon(MiuixIcons.Info, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) },
                            summary = "HyperMusic v$versionName",
                            onClick = onAboutClick
                        )
                    }
                }
            }
        }
    }
    OverlayDialog(show = showAccountLimitDialog, title = "无法关闭多账号",
        summary = "当前账号数量大于1，需要删除部分账号才能关闭多账号功能。",
        onDismissRequest = { showAccountLimitDialog = false }) {
        TextButton(text = "确定", onClick = { showAccountLimitDialog = false }, modifier = Modifier.fillMaxWidth())
    }

}
