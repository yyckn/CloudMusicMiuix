package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.R
import com.hypermusic.miuix.ui.components.SettingsItemIcon
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.data.api.NeteaseApi
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.hypermusic.miuix.ui.FeedBottomPads
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
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
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown" }.getOrDefault("unknown")
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
                onSuccess = {
                    onExtraAccountChanged()
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
                item {
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        ArrowPreference(title = "管理账号", summary = "添加、切换和管理账号", onClick = onManageAccountsClick,
                            startAction = { Icon(MiuixIcons.ConvertFile, contentDescription = null, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) })
                        if (isNetease) ArrowPreference(title = "听歌记录", summary = "播放历史与听歌足迹", onClick = onListeningHistoryClick,
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


}
