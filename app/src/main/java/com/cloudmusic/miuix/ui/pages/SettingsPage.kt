package com.cloudmusic.miuix.ui.pages

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.data.api.NeteaseApi
import com.cloudmusic.miuix.data.model.UserProfile
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

@Composable
fun SettingsPage(
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    onThemeSettingsClick: () -> Unit,
    onPlayPageSettingsClick: () -> Unit = {},
    onAboutClick: () -> Unit,
    onLyriconAdapterClick: () -> Unit = {},
    onManageAccountsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isMultiAccountEnabled by remember { 
        mutableStateOf(
            context.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("multi_account_enabled", false)
        ) 
    }
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
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
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
        if (isRefreshing && isLoggedIn) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                largeTitle = "设置",
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false
            )
        }
    ) { paddingValues ->
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            pullToRefreshState = pullToRefreshState,
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf(
                "继续下拉以刷新",
                "松手后刷新",
                "正在刷新...",
                "刷新成功"
            ),
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(bottom = 80.dp),
                overscrollEffect = null
            ) {
                item { Spacer(modifier = Modifier.size(12.dp)) }
                item { SmallTitle("账号") }
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
                                            error = painterResource(android.R.drawable.ic_menu_gallery)
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
                                    isMultiAccountEnabled = it
                                    // 保存状态
                                    context.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("multi_account_enabled", it)
                                        .apply()
                                },
                                title = "多账号切换",
                                summary = if (isMultiAccountEnabled) "已开启" else "未开启"
                            )
                        }
                        
                        // 多账号开启后显示管理选项
                        if (isLoggedIn && isMultiAccountEnabled) {
                            ArrowPreference(
                                title = "管理多账号",
                                summary = "添加、切换和管理账号",
                                onClick = onManageAccountsClick
                            )
                        }
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
                            title = "主题设置",
                            summary = "主题模式、底栏等",
                            onClick = onThemeSettingsClick
                        )
                        ArrowPreference(
                            title = "播放页设置",
                            summary = "歌词",
                            onClick = onPlayPageSettingsClick
                        )
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
                            title = "关于",
                            summary = "CloudMusic Miuix v$versionName",
                            onClick = onAboutClick
                        )
                        ArrowPreference(
                            title = "歌词接口适配",
                            summary = "词幕、LyricInfo",
                            onClick = onLyriconAdapterClick
                        )
                    }
                }
            }
        }
    }
}
