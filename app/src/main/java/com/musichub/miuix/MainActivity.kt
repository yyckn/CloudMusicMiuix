package com.musichub.miuix

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musichub.miuix.data.api.NeteaseApi
import com.musichub.miuix.data.lyrics.LyricUpgradeBlacklist
import com.musichub.miuix.data.lyrics.LyricUpgradeRequiredList
import com.musichub.miuix.data.lyrics.LyricExclusionRules
import com.musichub.miuix.ui.components.PlayerBar
import com.musichub.miuix.ui.pages.AboutPage
import com.musichub.miuix.ui.pages.AccountInfo
import com.musichub.miuix.ui.pages.AlbumPage
import com.musichub.miuix.ui.pages.AppIconPage
import com.musichub.miuix.ui.pages.ArtistPage
import com.musichub.miuix.ui.pages.CommentPage
import com.musichub.miuix.ui.pages.LoginPage
import com.musichub.miuix.ui.pages.LyriconAdapterPage
import com.musichub.miuix.ui.pages.LyricSettingsPage
import com.musichub.miuix.ui.pages.DeveloperOptionsPage
import com.musichub.miuix.ui.pages.UserAgreementPage
import com.musichub.miuix.ui.pages.LyricExclusionRulesPage
import com.musichub.miuix.ui.pages.LyricUpgradeManagePage
import com.musichub.miuix.ui.pages.ManageAccountsPage
import com.musichub.miuix.ui.pages.PlayPageSettingsPage
import com.musichub.miuix.ui.pages.PlayerPage
import com.musichub.miuix.ui.pages.PlaylistDetailPage
import com.musichub.miuix.ui.pages.PlaylistPage
import com.musichub.miuix.ui.pages.ProfilePage
import com.musichub.miuix.ui.pages.QrLoginPage
import com.musichub.miuix.ui.pages.RecommendPage
import com.musichub.miuix.ui.pages.SettingsPage
import com.musichub.miuix.ui.pages.SongSettingsPage
import com.musichub.miuix.ui.pages.ThemeSettingsPage
import com.musichub.miuix.ui.pages.TrackItem
import com.musichub.miuix.ui.pages.UpdateHistoryPage
import com.musichub.miuix.ui.components.BlurredBar
import com.musichub.miuix.ui.components.FloatingBottomBar
import com.musichub.miuix.ui.components.FloatingBottomBarItem
import com.musichub.miuix.ui.components.rememberBlurBackdrop
import com.musichub.miuix.ui.theme.LocalEnableBlur
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.musichub.miuix.ui.components.PlayerBar
import com.musichub.miuix.ui.components.SwipeablePlayerSheet
import com.musichub.miuix.ui.theme.CloudMusicMiuixTheme
import com.musichub.miuix.ui.theme.AgreementSettings
import com.musichub.miuix.ui.theme.KeyboardShortcutSettings
import com.musichub.miuix.ui.theme.ThemeSettings
import com.musichub.miuix.ui.theme.AppIconSettings
import com.musichub.miuix.viewmodel.MusicPlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

sealed class Screen {
    object Main : Screen()
    data class PlaylistDetail(
        val coverUrl: String = "",
        val title: String = ""
    ) : Screen()
    object Login : Screen()
    object QrLogin : Screen()
    object ThemeSettings : Screen()
    object PlayPageSettings : Screen()
    object SongSettings : Screen()
    object About : Screen()
    object Profile : Screen()
    data class Artist(val artistId: String) : Screen()
    data class Album(val albumId: Long) : Screen()
    object LyriconAdapter : Screen()
    object LyricSettings : Screen()
    object LyricUpgradeManage : Screen()
    object LyricRules : Screen()
    object DeveloperOptions : Screen()
    object AppIcon : Screen()
    object ManageAccounts : Screen()
    object UpdateHistory : Screen()
    data class Comment(val songId: String, val songTitle: String) : Screen()
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NeteaseApi.init(applicationContext)
        ThemeSettings.init(applicationContext)
        AppIconSettings.init(applicationContext)
        AppIconSettings.applySaved(applicationContext)
        LyricUpgradeBlacklist.init(applicationContext)
        LyricUpgradeRequiredList.init(applicationContext)
        LyricExclusionRules.init(applicationContext)
        KeyboardShortcutSettings.init(applicationContext)
        AgreementSettings.init(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent {
            CloudMusicMiuixTheme {
                if (!AgreementSettings.accepted) {
                    UserAgreementPage(
                        onAccept = { AgreementSettings.accept() }
                    )
                } else {
                    val windowSizeClass = calculateWindowSizeClass(this)
                    MainScreen(windowSizeClass)
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    windowSizeClass: WindowSizeClass,
    viewModel: MusicPlayerViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var screenStack by remember { mutableStateOf<List<Screen>>(emptyList()) }
    var isLoggedIn by remember { mutableStateOf(NeteaseApi.isLoggedIn()) }
    var profileVersion by remember { mutableIntStateOf(0) } // 用于触发UI刷新
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showPlayerPage by remember { mutableStateOf(false) }
    var isNavigatingBack by remember { mutableStateOf(false) }
    val tabs = listOf("推荐", "收藏", "设置")
    val icons = listOf(MiuixIcons.Home, MiuixIcons.ListView, MiuixIcons.Settings)
    val railState = rememberNavigationRailState(initialValue = NavigationRailValue.Expanded)
    
    // 宽度阈值：>600dp 视为平板；500–600dp 为阔屏手机；<500dp 为普通手机
    val configuration = LocalConfiguration.current
    val isTabletWidth = configuration.screenWidthDp > 600
    val useFloatingBar = ThemeSettings.useFloatingBar
    val floatingBarAlignment = ThemeSettings.floatingBarAlignmentIndex
    val systemNavBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 关闭悬浮底栏时，平板使用侧栏；打开悬浮底栏时全部使用悬浮样式
    val useNavigationRail = !useFloatingBar && isTabletWidth

    // 悬浮底栏上滑吸附状态（仅手机）
    var floatingBarAttached by remember { mutableStateOf(false) }
    LaunchedEffect(useFloatingBar, isTabletWidth, currentScreen) {
        if (!useFloatingBar || isTabletWidth || currentScreen !is Screen.Main) {
            floatingBarAttached = false
        }
    }

    // 滑动页面时自动折叠/展开悬浮底栏（仅手机）
    val pageScrollThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val pageScrollConnection = remember(useFloatingBar, isTabletWidth, pageScrollThreshold) {
        object : NestedScrollConnection {
            private var accumulated = 0f

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || !useFloatingBar || isTabletWidth) {
                    return Offset.Zero
                }
                // 手指上滑 = 页面向下滚动
                accumulated += consumed.y
                if (accumulated < -pageScrollThreshold) {
                    floatingBarAttached = true
                    accumulated = 0f
                } else if (accumulated > pageScrollThreshold) {
                    floatingBarAttached = false
                    accumulated = 0f
                }
                return Offset.Zero
            }
        }
    }

    // 迷你播放条圆角/封面样式过渡：开启悬浮底栏后所有页面保持悬浮样式
    val playerBarFloatingProgress by animateFloatAsState(
        targetValue = if (useFloatingBar) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "playerBarFloatingProgress"
    )

    // 底栏模糊 / 液态玻璃所需的背景层
    val enableBlur = LocalEnableBlur.current
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val surfaceColor = MiuixTheme.colorScheme.surface
    val liquidBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    // 二级页面背景层（悬浮态用于液态玻璃，非悬浮态用于背景模糊）
    val secondaryBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val secondaryFloatingLiquidBackdrop = if (useFloatingBar && enableBlur) {
        secondaryBackdrop
    } else {
        null
    }
    val secondaryBlurBackdrop = if (!useFloatingBar && enableBlur) {
        secondaryBackdrop
    } else {
        null
    }
    val secondaryLayerModifier = if (enableBlur) {
        Modifier.layerBackdrop(secondaryBackdrop)
    } else {
        Modifier
    }

    // 导航到新页面的函数
    fun navigateTo(screen: Screen) {
        isNavigatingBack = false
        screenStack = screenStack + currentScreen
        currentScreen = screen
    }

    // 返回上一页的函数
    fun navigateBack(): Boolean {
        if (screenStack.isNotEmpty()) {
            isNavigatingBack = true
            currentScreen = screenStack.last()
            screenStack = screenStack.dropLast(1)
            return true
        }
        return false
    }

    // 设置用户信息变化监听器
    LaunchedEffect(Unit) {
        NeteaseApi.setOnProfileChangedListener {
            profileVersion++
            viewModel.refreshAccountState()
        }
    }

    // 收集ViewModel的toast消息
    val toastContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        for (msg in viewModel.toastMessage) {
            android.widget.Toast.makeText(toastContext, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    SharedTransitionLayout {
    Box(modifier = Modifier
        .fillMaxSize()
        .clickable(enabled = false) {} // 确保点击事件正确传递
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (isNavigatingBack) {
                    // 返回动画：当前页面从左侧滑出，上一个页面从左侧滑入
                    slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                } else {
                    // 前进动画：新页面从右侧滑入，当前页面向左滑出
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                }
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                is Screen.Main -> {
                    if (useNavigationRail) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            NavigationRail(
                                state = railState
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    NavigationRailItem(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        icon = icons[index],
                                        label = tab
                                    )
                                }
                            }
                            Scaffold(
                                bottomBar = {
                                    PlayerBar(
                                        playerState = viewModel.playerState,
                                        onTogglePlayPause = { viewModel.togglePlayPause() },
                                        onPlayerBarClick = { showPlayerPage = true },
                                        onSwipeUp = { showPlayerPage = true },
                                        onPlaylistClick = { showPlaylistSheet = true },
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        hasBottomBar = false,
                                        hidePlaylistButton = viewModel.isFMMode || viewModel.isIntelligenceMode,
                            isFloatingStyle = useFloatingBar,
                            liquidBackdrop = secondaryFloatingLiquidBackdrop,
                            blurBackdrop = if (enableBlur) blurBackdrop else null,
                            floatingProgress = playerBarFloatingProgress,
                            floatingBottomMargin = 12.dp + systemNavBarPadding
                                    )
                                }
                            ) { padding ->
                                // 播放列表抽屉
                                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                                var shouldScrollToCurrent by remember { mutableStateOf(false) }
                                
                                // 处理定位到当前歌曲
                                LaunchedEffect(shouldScrollToCurrent) {
                                    if (shouldScrollToCurrent) {
                                        val currentIndex = viewModel.currentPlaylist.indexOfFirst { 
                                            it.id == viewModel.playerState.currentTrack?.id 
                                        }
                                        if (currentIndex >= 0) {
                                            listState.animateScrollToItem(currentIndex)
                                        }
                                        shouldScrollToCurrent = false
                                    }
                                }
                                
                                // 打开抽屉时自动定位到正在播放的歌曲
                                LaunchedEffect(showPlaylistSheet) {
                                    if (showPlaylistSheet) {
                                        val currentIndex = viewModel.currentPlaylist.indexOfFirst { 
                                            it.id == viewModel.playerState.currentTrack?.id 
                                        }
                                        if (currentIndex >= 0) {
                                            listState.scrollToItem(currentIndex)
                                        }
                                    }
                                }
                                
                                OverlayBottomSheet(
                                    show = showPlaylistSheet,
                                    title = "播放列表",
                                    insideMargin = DpSize(12.dp, 0.dp),
                                    startAction = null,
                                    endAction = {
                                        IconButton(
                                            onClick = { showPlaylistSheet = false }
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Close,
                                                contentDescription = "关闭",
                                                tint = MiuixTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    onDismissRequest = { showPlaylistSheet = false }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (viewModel.currentPlaylist.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "暂无歌曲",
                                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                )
                                            }
                                        } else {
                                            LazyColumn(
                                                state = listState,
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
                                            ) {
                                                items(viewModel.currentPlaylist) { track ->
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        colors = CardDefaults.defaultColors(
                                                            color = MiuixTheme.colorScheme.secondaryContainer
                                                        ),
                                                        pressFeedbackType = PressFeedbackType.Tilt,
                                                        showIndication = true,
                                                        onClick = {
                                                            viewModel.playTrack(track)
                                                            showPlaylistSheet = false
                                                        }
                                                    ) {
                                                        TrackItem(
                                                            track = track,
                                                            isPlaying = track.id == viewModel.playerState.currentTrack?.id,
                                                            onClick = {
                                                                viewModel.playTrack(track)
                                                                showPlaylistSheet = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        FloatingActionButton(
                                            onClick = { shouldScrollToCurrent = true },
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)
                                        ) {
                                            Icon(imageVector = MiuixIcons.Location, contentDescription = null, tint = MiuixTheme.colorScheme.onPrimary)
                                        }
                                    }
                                }
                                AnimatedContent(
                                    targetState = selectedTab,
                                    modifier = Modifier
                                        .then(
                                            if (blurBackdrop != null) {
                                                Modifier.layerBackdrop(blurBackdrop)
                                            } else Modifier
                                        ),
                                    transitionSpec = {
                                        if (targetState > initialState) {
                                            slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                                        } else {
                                            slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                                        }
                                    },
                                    label = "tab_transition"
                                ) { tab ->
                                    when (tab) {
                                    0 -> RecommendPage(
                                        recommendState = viewModel.recommendState,
                                        onPlaylistClick = { playlistId, coverUrl, title ->
                                            viewModel.loadPlaylistDetail(playlistId)
                                            navigateTo(Screen.PlaylistDetail(coverUrl, title))
                                        },
                                        viewModel = viewModel,
                                        isLoggedIn = isLoggedIn,
                                        isPlayerPageVisible = showPlayerPage,
                                        onOpenPlayerPage = { showPlayerPage = true },
                                        onIntelligenceMode = { songId, playlistId ->
                                            showPlayerPage = true
                                            viewModel.enterIntelligenceMode(songId, playlistId)
                                        },
                                        onArtistClick = { artistId -> navigateTo(Screen.Artist(artistId)) },
                                        onAlbumClick = { albumId -> navigateTo(Screen.Album(albumId)) },
                                        modifier = Modifier.padding(padding)
                                    )
                                1 -> PlaylistPage(
                                    recommendState = viewModel.recommendState,
                                    onPlaylistClick = { playlistId, coverUrl, title ->
                                        viewModel.loadPlaylistDetail(playlistId)
                                        navigateTo(Screen.PlaylistDetail(coverUrl, title))
                                    },
                                    onRefresh = { viewModel.loadUserPlaylists() },
                                    isLoggedIn = isLoggedIn,
                                    onLoadCloudDiskCover = { viewModel.loadCloudDiskCover() },
                                    onNavigateToAlbum = { albumId -> navigateTo(Screen.Album(albumId)) },
                                    onIntelligenceMode = { songId, playlistId ->
                                        showPlayerPage = true
                                        viewModel.enterIntelligenceMode(songId, playlistId)
                                    },
                                    modifier = Modifier.padding(padding)
                                )
                                2 -> SettingsPage(
                                    isLoggedIn = isLoggedIn,
                                    onLoginClick = {
                                        navigateTo(Screen.QrLogin)
                                    },
                                    onProfileClick = {
                                        navigateTo(Screen.Profile)
                                    },
                                    onThemeSettingsClick = {
                                        navigateTo(Screen.ThemeSettings)
                                    },
                                    onAppIconClick = {
                                        navigateTo(Screen.AppIcon)
                                    },
                                    onPlayPageSettingsClick = {
                                        navigateTo(Screen.PlayPageSettings)
                                    },
                                    onSongSettingsClick = {
                                        navigateTo(Screen.SongSettings)
                                    },
                                    onAboutClick = {
                                        navigateTo(Screen.About)
                                    },
                                    onLyriconAdapterClick = {
                                        navigateTo(Screen.LyriconAdapter)
                                    },
                                    onLyricSettingsClick = {
                                        navigateTo(Screen.LyricSettings)
                                    },
                                    onManageAccountsClick = {
                                        navigateTo(Screen.ManageAccounts)
                                    },
                                    modifier = Modifier.padding(padding)
                                )
                                    }
                                }
                            }
                        }
                    } else {
                        Scaffold(
                            bottomBar = {
                                val playerBar: @Composable (Modifier, Boolean, Dp, Boolean, Boolean, Boolean) -> Unit =
                                    { barModifier, floating, bottomMargin, compact, swipeEnabled, layoutSwipe ->
                                        val swipeUpAction: () -> Unit = if (layoutSwipe) {
                                            { floatingBarAttached = false }
                                        } else {
                                            { showPlayerPage = true }
                                        }
                                        val swipeDownAction: () -> Unit = if (layoutSwipe) {
                                            { floatingBarAttached = true }
                                        } else {
                                            {}
                                        }
                                        PlayerBar(
                                            playerState = viewModel.playerState,
                                            onTogglePlayPause = { viewModel.togglePlayPause() },
                                            onPlayerBarClick = { showPlayerPage = true },
                                            onSwipeUp = swipeUpAction,
                                            onSwipeDown = swipeDownAction,
                                            onPlaylistClick = { showPlaylistSheet = true },
                                            sharedTransitionScope = this@SharedTransitionLayout,
                                            animatedVisibilityScope = this@AnimatedContent,
                                            hasBottomBar = !floating,
                                            isFloatingStyle = floating,
                                            hidePlaylistButton = viewModel.isFMMode || viewModel.isIntelligenceMode,
                                            blurBackdrop = if (!floating && enableBlur) blurBackdrop else null,
                                            liquidBackdrop = if (floating && enableBlur) liquidBackdrop else null,
                                            floatingProgress = playerBarFloatingProgress,
                                            floatingBottomMargin = bottomMargin,
                                            compact = compact,
                                            enableSwipeUp = swipeEnabled,
                                            modifier = barModifier
                                        )
                                    }
                                if (useFloatingBar) {
                                    // 液态玻璃悬浮底栏
                                    val navBarBottomPadding = WindowInsets.navigationBars
                                        .asPaddingValues()
                                        .calculateBottomPadding()
                                    val defaultBottomPadding = if (navBarBottomPadding != 0.dp) {
                                        8.dp + navBarBottomPadding
                                    } else {
                                        28.dp
                                    }
                                    // 整体再向下移动 8dp，同时不低于系统导航栏
                                    val bottomPadding = (defaultBottomPadding - 8.dp)
                                        .coerceAtLeast(navBarBottomPadding)
                                    if (isTabletWidth) {
                                        // 平板：默认一行显示导航栏 + 迷你播放条
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    start = 16.dp,
                                                    end = 16.dp,
                                                    bottom = bottomPadding
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = when (floatingBarAlignment) {
                                                0 -> Arrangement.Start
                                                2 -> Arrangement.End
                                                else -> Arrangement.Center
                                            }
                                        ) {
                                            FloatingTabsBar(
                                                selectedIndex = selectedTab,
                                                onSelected = { selectedTab = it },
                                                backdrop = liquidBackdrop,
                                                isBlurEnabled = enableBlur,
                                                tabs = tabs,
                                                icons = icons,
                                                modifier = Modifier.pointerInput(Unit) {
                                                    detectTapGestures { }
                                                }
                                            )
                                            playerBar(Modifier.widthIn(max = 600.dp), true, 0.dp, false, true, false)
                                        }
                                    } else {
                                        // 手机：默认两行，滚动/下滑后折叠为一行并只保留封面
                                        BoxWithConstraints(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = bottomPadding)
                                        ) {
                                            val density = LocalDensity.current
                                            var navWidthPx by remember { mutableIntStateOf(0) }
                                            var navHeightPx by remember { mutableIntStateOf(0) }
                                            var playerHeightPx by remember { mutableIntStateOf(0) }
                                            val navWidth = if (navWidthPx > 0) with(density) { navWidthPx.toDp() } else 220.dp
                                            val navHeight = if (navHeightPx > 0) with(density) { navHeightPx.toDp() } else 64.dp
                                            val playerHeight = if (playerHeightPx > 0) with(density) { playerHeightPx.toDp() } else 66.dp
                                            val expandedHeight = playerHeight + 12.dp + navHeight
                                            val collapsedHeight = maxOf(playerHeight, navHeight)
                                            val collapsedGroupWidth = navWidth + 88.dp
                                            val alignedNavX = when (floatingBarAlignment) {
                                                0 -> 16.dp
                                                2 -> maxOf(16.dp, maxWidth - navWidth - 16.dp)
                                                else -> maxOf(16.dp, (maxWidth - navWidth) / 2f)
                                            }
                                            val collapsedGroupX = when (floatingBarAlignment) {
                                                0 -> 16.dp
                                                2 -> maxOf(16.dp, maxWidth - collapsedGroupWidth - 16.dp)
                                                else -> maxOf(16.dp, (maxWidth - collapsedGroupWidth) / 2f)
                                            }
                                            val collapsedPlayerX = collapsedGroupX + navWidth
                                            val containerHeight by animateDpAsState(
                                                targetValue = if (floatingBarAttached) collapsedHeight else expandedHeight,
                                                animationSpec = tween(durationMillis = 280),
                                                label = "bottomBarHeight"
                                            )
                                            val playerWidth by animateDpAsState(
                                                targetValue = if (floatingBarAttached) 88.dp else maxWidth,
                                                animationSpec = tween(durationMillis = 280),
                                                label = "playerWidth"
                                            )
                                            val playerX by animateDpAsState(
                                                targetValue = if (floatingBarAttached) collapsedPlayerX else 0.dp,
                                                animationSpec = tween(durationMillis = 280),
                                                label = "playerX"
                                            )
                                            val playerY by animateDpAsState(
                                                targetValue = if (floatingBarAttached) {
                                                    maxOf(0.dp, (collapsedHeight - playerHeight) / 2f)
                                                } else 0.dp,
                                                animationSpec = tween(durationMillis = 280),
                                                label = "playerY"
                                            )
                                            val navX by animateDpAsState(
                                                targetValue = if (floatingBarAttached) collapsedGroupX else alignedNavX,
                                                animationSpec = tween(durationMillis = 280),
                                                label = "navX"
                                            )
                                            val navY by animateDpAsState(
                                                targetValue = if (floatingBarAttached) {
                                                    maxOf(0.dp, (collapsedHeight - navHeight) / 2f)
                                                } else playerHeight + 12.dp,
                                                animationSpec = tween(durationMillis = 280),
                                                label = "navY"
                                            )
                                            val compactContent = playerWidth < maxWidth * 0.72f

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(containerHeight)
                                            ) {
                                                playerBar(
                                                    Modifier
                                                        .offset(x = playerX, y = playerY)
                                                        .width(playerWidth)
                                                        .onSizeChanged { playerHeightPx = it.height }
                                                        .zIndex(1f),
                                                    true,
                                                    0.dp,
                                                    compactContent,
                                                    true,
                                                    true
                                                )
                                                FloatingTabsBar(
                                                    selectedIndex = selectedTab,
                                                    onSelected = { selectedTab = it },
                                                    backdrop = liquidBackdrop,
                                                    isBlurEnabled = enableBlur,
                                                    tabs = tabs,
                                                    icons = icons,
                                                    modifier = Modifier
                                                        .offset(x = navX, y = navY)
                                                        .onSizeChanged {
                                                            navWidthPx = it.width
                                                            navHeightPx = it.height
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectTapGestures { }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Column {
                                        playerBar(Modifier, false, 12.dp, false, true, false)
                                        // 普通底栏（半透明模糊）
                                        BlurredBar(backdrop = blurBackdrop) {
                                            NavigationBar(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = if (blurBackdrop != null) {
                                                    Color.Transparent
                                                } else {
                                                    MiuixTheme.colorScheme.surface
                                                }
                                            ) {
                                                tabs.forEachIndexed { index, tab ->
                                                    NavigationBarItem(
                                                        selected = selectedTab == index,
                                                        onClick = { selectedTab = index },
                                                        icon = icons[index],
                                                        label = tab
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        ) { padding ->
                            // 播放列表抽屉
                            val listState = rememberLazyListState()
                            var shouldScrollToCurrent by remember { mutableStateOf(false) }
                            
                            // 处理定位到当前歌曲
                            LaunchedEffect(shouldScrollToCurrent) {
                                if (shouldScrollToCurrent) {
                                    val currentIndex = viewModel.currentPlaylist.indexOfFirst { 
                                        it.id == viewModel.playerState.currentTrack?.id 
                                    }
                                    if (currentIndex >= 0) {
                                        listState.animateScrollToItem(currentIndex)
                                    }
                                    shouldScrollToCurrent = false
                                }
                            }
                            
                            // 打开抽屉时自动定位到正在播放的歌曲
                            LaunchedEffect(showPlaylistSheet) {
                                if (showPlaylistSheet) {
                                    val currentIndex = viewModel.currentPlaylist.indexOfFirst { 
                                        it.id == viewModel.playerState.currentTrack?.id 
                                    }
                                    if (currentIndex >= 0) {
                                        listState.scrollToItem(currentIndex)
                                    }
                                }
                            }
                            
                            OverlayBottomSheet(
                                show = showPlaylistSheet,
                                title = "播放列表",
                                insideMargin = DpSize(12.dp, 0.dp),
                                startAction = null,
                                endAction = {
                                    IconButton(
                                        onClick = { showPlaylistSheet = false }
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Close,
                                            contentDescription = "关闭",
                                            tint = MiuixTheme.colorScheme.onSurface
                                        )
                                    }
                                },
                                onDismissRequest = { showPlaylistSheet = false }
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (viewModel.currentPlaylist.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "暂无歌曲",
                                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
                                        ) {
                                            items(viewModel.currentPlaylist) { track ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    colors = CardDefaults.defaultColors(
                                                        color = MiuixTheme.colorScheme.secondaryContainer
                                                    ),
                                                    pressFeedbackType = PressFeedbackType.Tilt,
                                                    showIndication = true,
                                                    onClick = {
                                                        viewModel.playTrack(track)
                                                        showPlaylistSheet = false
                                                    }
                                                ) {
                                                    TrackItem(
                                                        track = track,
                                                        isPlaying = track.id == viewModel.playerState.currentTrack?.id,
                                                        onClick = {
                                                            viewModel.playTrack(track)
                                                            showPlaylistSheet = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    FloatingActionButton(
                                        onClick = { shouldScrollToCurrent = true },
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)
                                    ) {
                                        Icon(imageVector = MiuixIcons.Location, contentDescription = null, tint = MiuixTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                            AnimatedContent(
                                targetState = selectedTab,
                                modifier = Modifier
                                    .then(
                                        if (blurBackdrop != null) {
                                            Modifier.layerBackdrop(blurBackdrop)
                                        } else Modifier
                                    )
                                    .then(
                                        if (ThemeSettings.useFloatingBar && enableBlur) {
                                            Modifier.layerBackdrop(liquidBackdrop)
                                        } else Modifier
                                    )
                                    .nestedScroll(pageScrollConnection),
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                                    } else {
                                        slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                                    }
                                },
                                label = "tab_transition"
                            ) { tab ->
                                when (tab) {
                                    0 -> RecommendPage(
                                    recommendState = viewModel.recommendState,
                                    onPlaylistClick = { playlistId, coverUrl, title ->
                                        viewModel.loadPlaylistDetail(playlistId)
                                        navigateTo(Screen.PlaylistDetail(coverUrl, title))
                                    },
                                    viewModel = viewModel,
                                    isLoggedIn = isLoggedIn,
                                    isPlayerPageVisible = showPlayerPage,
                                    onOpenPlayerPage = { showPlayerPage = true },
                                    onIntelligenceMode = { songId, playlistId ->
                                        showPlayerPage = true
                                        viewModel.enterIntelligenceMode(songId, playlistId)
                                    },
                                    modifier = Modifier.padding(padding)
                                )
                                1 -> PlaylistPage(
                                    recommendState = viewModel.recommendState,
                                    onPlaylistClick = { playlistId, coverUrl, title ->
                                        viewModel.loadPlaylistDetail(playlistId)
                                        navigateTo(Screen.PlaylistDetail(coverUrl, title))
                                    },
                                    onRefresh = { viewModel.loadUserPlaylists() },
                                    isLoggedIn = isLoggedIn,
                                    onLoadCloudDiskCover = { viewModel.loadCloudDiskCover() },
                                    onNavigateToAlbum = { albumId -> navigateTo(Screen.Album(albumId)) },
                                    onIntelligenceMode = { songId, playlistId ->
                                        showPlayerPage = true
                                        viewModel.enterIntelligenceMode(songId, playlistId)
                                    },
                                    modifier = Modifier.padding(padding)
                                )
                                2 -> SettingsPage(
                                    isLoggedIn = isLoggedIn,
                                    onLoginClick = {
                                        navigateTo(Screen.QrLogin)
                                    },
                                    onProfileClick = {
                                        navigateTo(Screen.Profile)
                                    },
                                    onThemeSettingsClick = {
                                        navigateTo(Screen.ThemeSettings)
                                    },
                                    onAppIconClick = {
                                        navigateTo(Screen.AppIcon)
                                    },
                                    onPlayPageSettingsClick = {
                                        navigateTo(Screen.PlayPageSettings)
                                    },
                                    onSongSettingsClick = {
                                        navigateTo(Screen.SongSettings)
                                    },
                                    onAboutClick = {
                                        navigateTo(Screen.About)
                                    },
                                    onLyriconAdapterClick = {
                                        navigateTo(Screen.LyriconAdapter)
                                    },
                                    onLyricSettingsClick = {
                                        navigateTo(Screen.LyricSettings)
                                    },
                                    onManageAccountsClick = {
                                        navigateTo(Screen.ManageAccounts)
                                    },
                                    modifier = Modifier.padding(padding)
                                )
                                }
                            }
                        }
                    }
                }
                is Screen.PlaylistDetail -> {
                    Scaffold(
                        bottomBar = {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = when (floatingBarAlignment) {
                                    0 -> Alignment.CenterStart
                                    2 -> Alignment.CenterEnd
                                    else -> Alignment.Center
                                }
                            ) {
                            PlayerBar(
                                playerState = viewModel.playerState,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onPlayerBarClick = { showPlayerPage = true },
                                onSwipeUp = { showPlayerPage = true },
                                onPlaylistClick = { showPlaylistSheet = true },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
                                hasBottomBar = false,
                                hidePlaylistButton = viewModel.isFMMode || viewModel.isIntelligenceMode,
                            isFloatingStyle = useFloatingBar,
                            liquidBackdrop = secondaryFloatingLiquidBackdrop,
                            blurBackdrop = secondaryBlurBackdrop,
                            floatingProgress = playerBarFloatingProgress,
                            floatingBottomMargin = 12.dp + systemNavBarPadding,
                            modifier = if (useFloatingBar && isTabletWidth) Modifier.widthIn(max = 600.dp) else Modifier
                            )
                            }
                        }
                    ) { padding ->
                        // 播放列表抽屉
                        val listState = rememberLazyListState()
                        var shouldScrollToCurrent by remember { mutableStateOf(false) }
                        
                        // 处理定位到当前歌曲
                        LaunchedEffect(shouldScrollToCurrent) {
                            if (shouldScrollToCurrent) {
                                val currentIndex = viewModel.currentPlaylist.indexOfFirst { 
                                    it.id == viewModel.playerState.currentTrack?.id 
                                }
                                if (currentIndex >= 0) {
                                    listState.animateScrollToItem(currentIndex)
                                }
                                shouldScrollToCurrent = false
                            }
                        }
                        
                        // 打开抽屉时自动定位到正在播放的歌曲
                        LaunchedEffect(showPlaylistSheet) {
                            if (showPlaylistSheet) {
                                val currentIndex = viewModel.currentPlaylist.indexOfFirst { 
                                    it.id == viewModel.playerState.currentTrack?.id 
                                }
                                if (currentIndex >= 0) {
                                    listState.scrollToItem(currentIndex)
                                }
                            }
                        }
                        
                        OverlayBottomSheet(
                            show = showPlaylistSheet,
                            title = "播放列表",
                            insideMargin = DpSize(12.dp, 0.dp),
                            startAction = null,
                            endAction = {
                                IconButton(
                                    onClick = { showPlaylistSheet = false }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Close,
                                        contentDescription = "关闭",
                                        tint = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onDismissRequest = { showPlaylistSheet = false }
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (viewModel.currentPlaylist.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "暂无歌曲",
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
                                    ) {
                                        items(viewModel.currentPlaylist) { track ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                colors = CardDefaults.defaultColors(
                                                    color = MiuixTheme.colorScheme.secondaryContainer
                                                ),
                                                pressFeedbackType = PressFeedbackType.Tilt,
                                                showIndication = true,
                                                onClick = {
                                                    viewModel.playTrack(track)
                                                    showPlaylistSheet = false
                                                }
                                            ) {
                                                TrackItem(
                                                    track = track,
                                                    isPlaying = track.id == viewModel.playerState.currentTrack?.id,
                                                    onClick = {
                                                        viewModel.playTrack(track)
                                                        showPlaylistSheet = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                FloatingActionButton(
                                    onClick = { shouldScrollToCurrent = true },
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)
                                ) {
                                    Icon(imageVector = MiuixIcons.Location, contentDescription = null, tint = MiuixTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                        val context = LocalContext.current
                        PlaylistDetailPage(
                            playlistDetail = viewModel.playlistDetailState.detail,
                            isLoading = viewModel.playlistDetailState.isLoading,
                            error = viewModel.playlistDetailState.error,
                            currentTrack = viewModel.playerState.currentTrack,
                            isCurrentTrackLiked = viewModel.isCurrentTrackLiked,
                            initialCoverUrl = screen.coverUrl,
                            initialTitle = screen.title,
                            isCloudDisk = viewModel.playlistDetailState.detail?.info?.id == "cloud_disk",
                            isLoadingMore = viewModel.isCloudDiskLoadingMore,
                            onLoadMore = { viewModel.loadMoreCloudDiskSongs() },
                            onBackClick = {
                                viewModel.clearPlaylistDetail()
                                if (!navigateBack()) {
                                    currentScreen = Screen.Main
                                }
                            },
                            onTrackClick = { track ->
                                // 点击歌曲时替换播放列表
                                val tracks = viewModel.playlistDetailState.detail?.tracks
                                if (tracks != null) {
                                    viewModel.playTrackWithMode(track, tracks)
                                } else {
                                    viewModel.playTrack(track)
                                }
                            },
                            onPlayAllClick = { tracks ->
                                if (tracks.isNotEmpty()) {
                                    viewModel.playTrackWithMode(tracks.first(), tracks)
                                }
                            },
                            onShare = { track ->
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}\n${track.sourceUrl}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "分享歌曲"))
                            },
                            onLikeTrack = {
                                viewModel.likeCurrentTrack { isLiked ->
                                    val message = if (isLiked) "已收" else "已取消收"
                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDownload = { viewModel.downloadCurrentTrack() },
                            onDownloadCover = {
                                val coverUrl = viewModel.playerState.currentTrack?.imgUrl
                                if (!coverUrl.isNullOrEmpty()) {
                                    try {
                                        val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        val fileName = "${viewModel.playerState.currentTrack?.title ?: "cover"}.jpg"
                                        val request = android.app.DownloadManager.Request(android.net.Uri.parse(coverUrl))
                                            .setTitle("下载封面图片")
                                            .setDescription("正在下载封面图片")
                                            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "CloudMusic/$fileName")
                                        downloadManager.enqueue(request)
                                        android.widget.Toast.makeText(context, "已保存到 /Pictures/CloudMusic/ 目录", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "保存失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onDownloadAndShare = { viewModel.downloadAndShareCurrentTrack() },
                            onDownloadCoverAndShare = { viewModel.downloadAndShareCover() },
                            onNavigateToArtist = { artistId ->
                                navigateTo(Screen.Artist(artistId))
                            },
                            onNavigateToAlbum = { albumId ->
                                navigateTo(Screen.Album(albumId))
                            },
                            onNavigateToComment = { songId, songTitle ->
                                navigateTo(Screen.Comment(songId, songTitle))
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                            isBackHandlerEnabled = !showPlayerPage,
                            modifier = Modifier.then(secondaryLayerModifier)
                        )
                    }
                }
            is Screen.Login -> {
                LoginPage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    },
                    onLoginSuccess = { cookies ->
                        NeteaseApi.setCookie(cookies)
                        isLoggedIn = true
                        currentScreen = Screen.Main
                        screenStack = emptyList()
                        viewModel.loadRecommendPlaylists()
                    }
                )
            }
            is Screen.QrLogin -> {
                QrLoginPage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    },
                    onLoginSuccess = { nickname ->
                        isLoggedIn = true
                        currentScreen = Screen.Main
                        screenStack = emptyList()
                        viewModel.loadRecommendPlaylists()
                        viewModel.loadUserPlaylists()
                        viewModel.loadUserInfo()
                        // 保存当前账号到账号列表
                        NeteaseApi.saveCurrentAccount()
                    },
                    context = androidx.compose.ui.platform.LocalContext.current
                )
            }
            is Screen.ThemeSettings -> {
                ThemeSettingsPage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    }
                )
            }
            is Screen.AppIcon -> {
                AppIconPage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    }
                )
            }
            is Screen.PlayPageSettings -> {
                PlayPageSettingsPage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    }
                )
            }
            is Screen.SongSettings -> {
                SongSettingsPage(
                    viewModel = viewModel,
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    }
                )
            }
            is Screen.About -> {
                AboutPage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    },
                    onOpenSourceClick = {
                        // TODO: 实现开放源代码许可页面
                    },
                    viewModel = viewModel,
                    onCheckUpdate = { viewModel.checkForUpdates(manual = true) },
                    onViewHistory = { navigateTo(Screen.UpdateHistory) },
                    onDeveloperOptionsClick = { navigateTo(Screen.DeveloperOptions) }
                )
            }
            is Screen.DeveloperOptions -> {
                DeveloperOptionsPage(
                    currentTrack = viewModel.playerState.currentTrack,
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    }
                )
            }
            is Screen.UpdateHistory -> {
                UpdateHistoryPage(
                    viewModel = viewModel,
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    }
                )
            }
            is Screen.LyriconAdapter -> {
                LyriconAdapterPage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    }
                )
            }
            is Screen.LyricSettings -> {
                LyricSettingsPage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    },
                    onWordByWordLyricsChanged = {
                        viewModel.resyncLyricsToLyricon()
                    },
                    onLyricSettingsChanged = {
                        viewModel.loadLyrics()
                    },
                    onManageLyricUpgradesClick = {
                        navigateTo(Screen.LyricUpgradeManage)
                    },
                    onExclusionRulesClick = {
                        navigateTo(Screen.LyricRules)
                    }
                )
            }
            is Screen.LyricUpgradeManage -> {
                LyricUpgradeManagePage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    },
                    onEntryRemoved = { removedId ->
                        if (viewModel.playerState.currentTrack?.id == removedId) {
                            viewModel.loadLyrics()
                        }
                    },
                    onRequiredEntryRemoved = { removedId ->
                        if (viewModel.playerState.currentTrack?.id == removedId) {
                            viewModel.loadLyrics()
                        }
                    }
                )
            }
            is Screen.LyricRules -> {
                LyricExclusionRulesPage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    },
                    onRulesChanged = {
                        viewModel.loadLyrics()
                    }
                )
            }
            is Screen.ManageAccounts -> {
                ManageAccountsPage(
                    accounts = NeteaseApi.getAccounts().map { account ->
                        AccountInfo(
                            userId = account.userId,
                            nickname = account.nickname,
                            avatarUrl = account.avatarUrl
                        )
                    },
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    },
                    onAccountClick = { userId ->
                        // 切换到选中的账号
                        if (NeteaseApi.switchToAccount(userId)) {
                            isLoggedIn = true
                            viewModel.loadUserInfo()
                            viewModel.loadUserPlaylists()
                            // 跳转到资料页
                            navigateTo(Screen.Profile)
                        }
                    },
                    onLoginNewAccount = {
                        // 保存当前账号的Cookie
                        NeteaseApi.saveCurrentAccount()
                        // 登录新账号
                        navigateTo(Screen.QrLogin)
                    },
                    playbackAccountIndex = viewModel.playbackAccountIndex,
                    dataAccountIndex = viewModel.dataAccountIndex,
                    onPlaybackAccountChange = { index ->
                        viewModel.updatePlaybackAccountIndex(index)
                    },
                    onDataAccountChange = { index ->
                        viewModel.updateDataAccountIndex(index)
                    }
                )
            }
            is Screen.Profile -> {
                ProfilePage(
                    onBackClick = {
                        if (!navigateBack()) {
                            currentScreen = Screen.Main
                        }
                    },
                    onLogout = {
                        val stillLoggedIn = NeteaseApi.logoutCurrentAccount()
                        isLoggedIn = stillLoggedIn
                        if (stillLoggedIn) {
                            viewModel.refreshAccountState()
                        }
                        currentScreen = Screen.Main
                    },
                    isLoggedIn = isLoggedIn,
                    profileVersion = profileVersion
                )
            }
            is Screen.Artist -> {
                Scaffold(
                    bottomBar = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = when (floatingBarAlignment) {
                                0 -> Alignment.CenterStart
                                2 -> Alignment.CenterEnd
                                else -> Alignment.Center
                            }
                        ) {
                        PlayerBar(
                            playerState = viewModel.playerState,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayerBarClick = { showPlayerPage = true },
                            onSwipeUp = { showPlayerPage = true },
                            onPlaylistClick = { showPlaylistSheet = true },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                            hasBottomBar = false,
                            hidePlaylistButton = viewModel.isFMMode || viewModel.isIntelligenceMode,
                            isFloatingStyle = useFloatingBar,
                            liquidBackdrop = secondaryFloatingLiquidBackdrop,
                            blurBackdrop = secondaryBlurBackdrop,
                            floatingProgress = playerBarFloatingProgress,
                            floatingBottomMargin = 12.dp + systemNavBarPadding,
                            modifier = if (useFloatingBar && isTabletWidth) Modifier.widthIn(max = 600.dp) else Modifier
                        )
                        }
                    }
                ) { padding ->
                    val context = LocalContext.current
                    // 播放列表抽屉
                    val artistPlaylistListState = rememberLazyListState()
                    var artistShouldScrollToCurrent by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(artistShouldScrollToCurrent) {
                        if (artistShouldScrollToCurrent) {
                            val currentIndex = viewModel.currentPlaylist.indexOfFirst { 
                                it.id == viewModel.playerState.currentTrack?.id 
                            }
                            if (currentIndex >= 0) {
                                artistPlaylistListState.animateScrollToItem(currentIndex)
                            }
                            artistShouldScrollToCurrent = false
                        }
                    }
                    
                    LaunchedEffect(showPlaylistSheet) {
                        if (showPlaylistSheet) {
                            val currentIndex = viewModel.currentPlaylist.indexOfFirst { 
                                it.id == viewModel.playerState.currentTrack?.id 
                            }
                            if (currentIndex >= 0) {
                                artistPlaylistListState.scrollToItem(currentIndex)
                            }
                        }
                    }
                    
                    OverlayBottomSheet(
                        show = showPlaylistSheet,
                        title = "播放列表",
                        insideMargin = DpSize(12.dp, 0.dp),
                        startAction = null,
                        endAction = {
                            IconButton(
                                onClick = { showPlaylistSheet = false }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Close,
                                    contentDescription = "关闭",
                                    tint = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        },
                        onDismissRequest = { showPlaylistSheet = false }
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (viewModel.currentPlaylist.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无歌曲",
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = artistPlaylistListState,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
                                ) {
                                    items(viewModel.currentPlaylist) { track ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.defaultColors(
                                                color = MiuixTheme.colorScheme.secondaryContainer
                                            ),
                                            pressFeedbackType = PressFeedbackType.Tilt,
                                            showIndication = true,
                                            onClick = {
                                                viewModel.playTrack(track)
                                                showPlaylistSheet = false
                                            }
                                        ) {
                                            TrackItem(
                                                track = track,
                                                isPlaying = track.id == viewModel.playerState.currentTrack?.id,
                                                onClick = {
                                                    viewModel.playTrack(track)
                                                    showPlaylistSheet = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            FloatingActionButton(
                                onClick = { artistShouldScrollToCurrent = true },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)
                            ) {
                                Icon(imageVector = MiuixIcons.Location, contentDescription = null, tint = MiuixTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    ArtistPage(
                        artistId = screen.artistId,
                        onBackClick = {
                            if (!navigateBack()) {
                                currentScreen = Screen.Main
                            }
                        },
                        onTrackClick = { track, tracks ->
                            viewModel.playTrackWithMode(track, tracks)
                        },
                        onAlbumClick = { albumId ->
                            navigateTo(Screen.Album(albumId))
                        },
                        currentTrack = viewModel.playerState.currentTrack,
                        currentPlaylist = viewModel.currentPlaylist,
                        isCurrentTrackLiked = viewModel.isCurrentTrackLiked,
                        onShare = { track ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}\n${track.sourceUrl}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享歌曲"))
                        },
                        onLikeTrack = {
                            viewModel.likeCurrentTrack { isLiked ->
                                val message = if (isLiked) "已收藏" else "已取消收藏"
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDownload = { viewModel.downloadCurrentTrack() },
                        onDownloadCover = {
                            val coverUrl = viewModel.playerState.currentTrack?.imgUrl
                            if (!coverUrl.isNullOrEmpty()) {
                                try {
                                    val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                    val fileName = "${viewModel.playerState.currentTrack?.title ?: "cover"}.jpg"
                                    val request = android.app.DownloadManager.Request(android.net.Uri.parse(coverUrl))
                                        .setTitle("下载封面图片")
                                        .setDescription("正在下载封面图片")
                                        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "CloudMusic/$fileName")
                                    downloadManager.enqueue(request)
                                    android.widget.Toast.makeText(context, "已保存到 /Pictures/CloudMusic/ 目录", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "保存失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDownloadAndShare = { viewModel.downloadAndShareCurrentTrack() },
                        onDownloadCoverAndShare = { viewModel.downloadAndShareCover() },
                        onNavigateToArtist = { artistId ->
                            navigateTo(Screen.Artist(artistId))
                        },
                        onNavigateToAlbum = { albumId ->
                            navigateTo(Screen.Album(albumId))
                        },
                        onNavigateToComment = { songId, songTitle ->
                            navigateTo(Screen.Comment(songId, songTitle))
                        },
                        onPlayTrack = { track ->
                            viewModel.playTrack(track)
                        },
                        isBackHandlerEnabled = !showPlayerPage,
                        modifier = Modifier.then(secondaryLayerModifier)
                    )
                }
            }
            is Screen.Album -> {
                Scaffold(
                    bottomBar = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = when (floatingBarAlignment) {
                                0 -> Alignment.CenterStart
                                2 -> Alignment.CenterEnd
                                else -> Alignment.Center
                            }
                        ) {
                        PlayerBar(
                            playerState = viewModel.playerState,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayerBarClick = { showPlayerPage = true },
                            onSwipeUp = { showPlayerPage = true },
                            onPlaylistClick = { showPlaylistSheet = true },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                            hasBottomBar = false,
                            hidePlaylistButton = viewModel.isFMMode || viewModel.isIntelligenceMode,
                            isFloatingStyle = useFloatingBar,
                            liquidBackdrop = secondaryFloatingLiquidBackdrop,
                            blurBackdrop = secondaryBlurBackdrop,
                            floatingProgress = playerBarFloatingProgress,
                            floatingBottomMargin = 12.dp + systemNavBarPadding,
                            modifier = if (useFloatingBar && isTabletWidth) Modifier.widthIn(max = 600.dp) else Modifier
                        )
                        }
                    }
                ) { padding ->
                    val context = LocalContext.current
                    // 播放列表抽屉
                    val albumPlaylistListState = rememberLazyListState()
                    var albumShouldScrollToCurrent by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(albumShouldScrollToCurrent) {
                        if (albumShouldScrollToCurrent) {
                            val currentIndex = viewModel.currentPlaylist.indexOfFirst { 
                                it.id == viewModel.playerState.currentTrack?.id 
                            }
                            if (currentIndex >= 0) {
                                albumPlaylistListState.animateScrollToItem(currentIndex)
                            }
                            albumShouldScrollToCurrent = false
                        }
                    }
                    
                    LaunchedEffect(showPlaylistSheet) {
                        if (showPlaylistSheet) {
                            val currentIndex = viewModel.currentPlaylist.indexOfFirst { 
                                it.id == viewModel.playerState.currentTrack?.id 
                            }
                            if (currentIndex >= 0) {
                                albumPlaylistListState.scrollToItem(currentIndex)
                            }
                        }
                    }
                    
                    OverlayBottomSheet(
                        show = showPlaylistSheet,
                        title = "播放列表",
                        insideMargin = DpSize(12.dp, 0.dp),
                        startAction = null,
                        endAction = {
                            IconButton(
                                onClick = { showPlaylistSheet = false }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Close,
                                    contentDescription = "关闭",
                                    tint = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        },
                        onDismissRequest = { showPlaylistSheet = false }
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (viewModel.currentPlaylist.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无歌曲",
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = albumPlaylistListState,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
                                ) {
                                    items(viewModel.currentPlaylist) { track ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.defaultColors(
                                                color = MiuixTheme.colorScheme.secondaryContainer
                                            ),
                                            pressFeedbackType = PressFeedbackType.Tilt,
                                            showIndication = true,
                                            onClick = {
                                                viewModel.playTrack(track)
                                                showPlaylistSheet = false
                                            }
                                        ) {
                                            TrackItem(
                                                track = track,
                                                isPlaying = track.id == viewModel.playerState.currentTrack?.id,
                                                onClick = {
                                                    viewModel.playTrack(track)
                                                    showPlaylistSheet = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            FloatingActionButton(
                                onClick = { albumShouldScrollToCurrent = true },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp)
                            ) {
                                Icon(imageVector = MiuixIcons.Location, contentDescription = null, tint = MiuixTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    AlbumPage(
                        albumId = screen.albumId,
                        onBackClick = {
                            if (!navigateBack()) {
                                currentScreen = Screen.Main
                            }
                        },
                        onTrackClick = { track, tracks ->
                            viewModel.playTrackWithMode(track, tracks)
                        },
                        onPlayAllClick = { tracks ->
                            if (tracks.isNotEmpty()) {
                                viewModel.playTrackWithMode(tracks.first(), tracks)
                            }
                        },
                        currentTrack = viewModel.playerState.currentTrack,
                        currentPlaylist = viewModel.currentPlaylist,
                        isCurrentTrackLiked = viewModel.isCurrentTrackLiked,
                        onShare = { track ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}\n${track.sourceUrl}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享歌曲"))
                        },
                        onLikeTrack = {
                            viewModel.likeCurrentTrack { isLiked ->
                                val message = if (isLiked) "已收藏" else "已取消收藏"
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDownload = { viewModel.downloadCurrentTrack() },
                        onDownloadCover = {
                            val coverUrl = viewModel.playerState.currentTrack?.imgUrl
                            if (!coverUrl.isNullOrEmpty()) {
                                try {
                                    val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                    val fileName = "${viewModel.playerState.currentTrack?.title ?: "cover"}.jpg"
                                    val request = android.app.DownloadManager.Request(android.net.Uri.parse(coverUrl))
                                        .setTitle("下载封面图片")
                                        .setDescription("正在下载封面图片")
                                        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "CloudMusic/$fileName")
                                    downloadManager.enqueue(request)
                                    android.widget.Toast.makeText(context, "已保存到 /Pictures/CloudMusic/ 目录", android.widget.Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "保存失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDownloadAndShare = { viewModel.downloadAndShareCurrentTrack() },
                        onDownloadCoverAndShare = { viewModel.downloadAndShareCover() },
                        onNavigateToArtist = { artistId ->
                            navigateTo(Screen.Artist(artistId))
                        },
                        onNavigateToComment = { songId, songTitle ->
                            navigateTo(Screen.Comment(songId, songTitle))
                        },
                        onPlayTrack = { track ->
                            viewModel.playTrack(track)
                        },
                        isBackHandlerEnabled = !showPlayerPage,
                        modifier = Modifier.then(secondaryLayerModifier)
                    )
                }
            }
            is Screen.Comment -> {
                Scaffold(
                    bottomBar = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = when (floatingBarAlignment) {
                                0 -> Alignment.CenterStart
                                2 -> Alignment.CenterEnd
                                else -> Alignment.Center
                            }
                        ) {
                        PlayerBar(
                            playerState = viewModel.playerState,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onPlayerBarClick = { showPlayerPage = true },
                            onSwipeUp = { showPlayerPage = true },
                            onPlaylistClick = { showPlaylistSheet = true },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                            hasBottomBar = false,
                            hidePlaylistButton = viewModel.isFMMode || viewModel.isIntelligenceMode,
                            isFloatingStyle = useFloatingBar,
                            liquidBackdrop = secondaryFloatingLiquidBackdrop,
                            blurBackdrop = secondaryBlurBackdrop,
                            floatingProgress = playerBarFloatingProgress,
                            floatingBottomMargin = 12.dp + systemNavBarPadding,
                            modifier = if (useFloatingBar && isTabletWidth) Modifier.widthIn(max = 600.dp) else Modifier
                        )
                        }
                    }
                ) { padding ->
                    CommentPage(
                        songId = screen.songId,
                        songTitle = screen.songTitle,
                        onBackClick = {
                            if (!navigateBack()) {
                                currentScreen = Screen.Main
                            }
                        },
                        modifier = Modifier.then(secondaryLayerModifier)
                    )
                }
            }
        }
        }

        // 播放器页面覆盖层 - 使用可滑动的底部弹出层
        var isPlayerDrawerOpen by remember { mutableStateOf(false) }
        SwipeablePlayerSheet(
            isVisible = showPlayerPage,
            onShow = { showPlayerPage = true },
            onDismiss = { showPlayerPage = false },
            enableSwipeToDismiss = !isPlayerDrawerOpen
        ) {
            val context = LocalContext.current
            PlayerPage(
                viewModel = viewModel,
                playerState = viewModel.playerState,
                lyricState = viewModel.lyricState,
                currentPlaylist = viewModel.currentPlaylist,
                isCurrentTrackLiked = viewModel.isCurrentTrackLiked,
                isFMMode = viewModel.isFMMode,
                isIntelligenceMode = viewModel.isIntelligenceMode,
                isVipAccount = viewModel.userVipType > 0,
                onBackClick = { showPlayerPage = false },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onPrevious = {
                    when {
                        viewModel.isFMMode -> viewModel.fmPlayPrevious()
                        viewModel.isIntelligenceMode -> viewModel.intelligencePlayPrevious()
                        else -> viewModel.playPrevious()
                    }
                },
                onNext = {
                    when {
                        viewModel.isFMMode -> viewModel.fmPlayNext()
                        viewModel.isIntelligenceMode -> viewModel.intelligencePlayNext()
                        else -> viewModel.playNext()
                    }
                },
                onSeekTo = { position -> viewModel.seekTo(position) },
                onDownload = { viewModel.downloadCurrentTrack() },
                onDownloadCover = {
                    val coverUrl = viewModel.playerState.currentTrack?.imgUrl
                    if (!coverUrl.isNullOrEmpty()) {
                        try {
                            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                            val fileName = "${viewModel.playerState.currentTrack?.title ?: "cover"}.jpg"
                            val request = android.app.DownloadManager.Request(android.net.Uri.parse(coverUrl))
                                .setTitle("下载封面图片")
                                .setDescription("正在下载封面图片")
                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "CloudMusic/$fileName")
                            downloadManager.enqueue(request)
                            android.widget.Toast.makeText(context, "已保存到 /Pictures/CloudMusic/ 目录", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to download cover: ${e.message}")
                        }
                    }
                },
                onDownloadAndShare = { viewModel.downloadAndShareCurrentTrack() },
                onDownloadCoverAndShare = { viewModel.downloadAndShareCover() },
                onShare = {
                    val track = viewModel.playerState.currentTrack
                    if (track != null) {
                        val songId = track.id.removePrefix("netrack_")
                        val shareText = "【网易云音乐】分享来自 @${track.artist} 的单曲《${track.title}》: https://music.163.com/#/song?id=$songId"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }
                },
                onPlayTrack = { track -> viewModel.playTrack(track) },
                onLoadLyrics = { viewModel.loadLyrics() },
                onOpenSystemUI = {
                    try {
                        // 先尝试小米的投屏Activity
                        val intent = Intent("miui.intent.action.ACTIVITY_MIPLAY_DETAIL")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.d("MainActivity", "小米投屏不可用，尝试原生API: ${e.message}")
                        try {
                            // 如果失败，使用原生安卓的媒体输出对话框API
                            val intent = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            android.util.Log.e("MainActivity", "原生投屏API也不可用: ${e2.message}")
                        }
                    }
                },
                onLikeTrack = {
                    viewModel.likeCurrentTrack { isLiked ->
                        val message = if (isLiked) "已收藏" else "已取消收藏"
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onDrawerStateChanged = { isOpen -> isPlayerDrawerOpen = isOpen },
                onNavigateToArtist = { artistId ->
                    showPlayerPage = false
                    navigateTo(Screen.Artist(artistId))
                },
                onNavigateToAlbum = { albumId ->
                    showPlayerPage = false
                    navigateTo(Screen.Album(albumId))
                },
                onNavigateToComment = { songId, songTitle ->
                    showPlayerPage = false
                    navigateTo(Screen.Comment(songId, songTitle))
                },
                playMode = viewModel.playMode,
                onSwitchPlayMode = { viewModel.switchPlayMode() }
            )
        }
    }
}
}

@Composable
private fun FloatingTabsBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    isBlurEnabled: Boolean,
    tabs: List<String>,
    icons: List<ImageVector>
) {
    FloatingBottomBar(
        modifier = modifier,
        selectedIndex = selectedIndex,
        onSelected = onSelected,
        backdrop = backdrop,
        tabsCount = tabs.size,
        isBlurEnabled = isBlurEnabled
    ) { activateTab ->
        tabs.forEachIndexed { index, tab ->
            FloatingBottomBarItem(
                selected = selectedIndex == index,
                onClick = { activateTab(index) },
                modifier = Modifier.defaultMinSize(minWidth = 76.dp)
            ) {
                Icon(
                    imageVector = icons[index],
                    contentDescription = tab
                )
                Text(
                    text = tab,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}
