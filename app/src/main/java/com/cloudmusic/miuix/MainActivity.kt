package com.cloudmusic.miuix

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudmusic.miuix.data.api.NeteaseApi
import com.cloudmusic.miuix.ui.components.PlayerBar
import com.cloudmusic.miuix.ui.pages.AboutPage
import com.cloudmusic.miuix.ui.pages.LoginPage
import com.cloudmusic.miuix.ui.pages.PlayPageSettingsPage
import com.cloudmusic.miuix.ui.pages.PlayerPage
import com.cloudmusic.miuix.ui.pages.PlaylistDetailPage
import com.cloudmusic.miuix.ui.pages.PlaylistPage
import com.cloudmusic.miuix.ui.pages.ProfilePage
import com.cloudmusic.miuix.ui.pages.QrLoginPage
import com.cloudmusic.miuix.ui.pages.RecommendPage
import com.cloudmusic.miuix.ui.pages.SettingsPage
import com.cloudmusic.miuix.ui.pages.ThemeSettingsPage
import com.cloudmusic.miuix.ui.pages.TrackItem
import com.cloudmusic.miuix.ui.components.LiquidGlassNavigationBar
import com.cloudmusic.miuix.ui.components.LiquidGlassNavigationBarItem
import com.cloudmusic.miuix.ui.components.PlayerBar
import com.cloudmusic.miuix.ui.components.SwipeablePlayerSheet
import com.cloudmusic.miuix.ui.theme.CloudMusicMiuixTheme
import com.cloudmusic.miuix.ui.theme.ThemeSettings
import com.cloudmusic.miuix.viewmodel.MusicPlayerViewModel
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
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
    object About : Screen()
    object Profile : Screen()
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NeteaseApi.init(applicationContext)
        ThemeSettings.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            CloudMusicMiuixTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                MainScreen(windowSizeClass)
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
    var isLoggedIn by remember { mutableStateOf(NeteaseApi.isLoggedIn()) }
    var profileVersion by remember { mutableIntStateOf(0) } // 用于触发UI刷新
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showPlayerPage by remember { mutableStateOf(false) }
    val tabs = listOf("推荐", "歌单", "设置")
    val icons = listOf(MiuixIcons.Home, MiuixIcons.ListView, MiuixIcons.Settings)
    val railState = rememberNavigationRailState(initialValue = NavigationRailValue.Expanded)
    val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    // 设置用户信息变化监听器
    LaunchedEffect(Unit) {
        NeteaseApi.setOnProfileChangedListener {
            profileVersion++
        }
    }

    SharedTransitionLayout {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                when {
                    initialState is Screen.Main && targetState is Screen.PlaylistDetail ->
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                    initialState is Screen.PlaylistDetail && targetState is Screen.Main ->
                        slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                    initialState is Screen.Main && targetState is Screen.Login ->
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                    initialState is Screen.Login && targetState is Screen.Main ->
                        slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                    initialState is Screen.Main && targetState is Screen.QrLogin ->
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                    initialState is Screen.QrLogin && targetState is Screen.Main ->
                        slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                    initialState is Screen.Main && targetState is Screen.ThemeSettings ->
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                    initialState is Screen.ThemeSettings && targetState is Screen.Main ->
                        slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                    initialState is Screen.Main && targetState is Screen.PlayPageSettings ->
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                    initialState is Screen.PlayPageSettings && targetState is Screen.Main ->
                        slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                    initialState is Screen.Main && targetState is Screen.About ->
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                    initialState is Screen.About && targetState is Screen.Main ->
                        slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                    else -> slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
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
                                        hasBottomBar = false
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
                                    startAction = {
                                        IconButton(
                                            onClick = { shouldScrollToCurrent = true }
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Location,
                                                contentDescription = "定位到当前歌曲",
                                                tint = MiuixTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
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
                                }
                                AnimatedContent(
                                    targetState = selectedTab,
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
                                            currentScreen = Screen.PlaylistDetail(coverUrl, title)
                                        },
                                        viewModel = viewModel,
                                        isLoggedIn = isLoggedIn,
                                        isPlayerPageVisible = showPlayerPage,
                                        onOpenPlayerPage = { showPlayerPage = true },
                                        modifier = Modifier.padding(padding)
                                    )
                                        1 -> PlaylistPage(
                                            recommendState = viewModel.recommendState,
                                            onPlaylistClick = { playlistId, coverUrl, title ->
                                                viewModel.loadPlaylistDetail(playlistId)
                                                currentScreen = Screen.PlaylistDetail(coverUrl, title)
                                            },
                                            onRefresh = { viewModel.loadUserPlaylists() },
                                            isLoggedIn = isLoggedIn,
                                            modifier = Modifier.padding(padding)
                                        )
                                2 -> SettingsPage(
                                    isLoggedIn = isLoggedIn,
                                    onLoginClick = {
                                        currentScreen = Screen.Login
                                    },
                                    onProfileClick = {
                                        currentScreen = Screen.Profile
                                    },
                                    onThemeSettingsClick = {
                                        currentScreen = Screen.ThemeSettings
                                    },
                                    onPlayPageSettingsClick = {
                                        currentScreen = Screen.PlayPageSettings
                                    },
                                    onAboutClick = {
                                        currentScreen = Screen.About
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
                                Column {
                                    PlayerBar(
                                        playerState = viewModel.playerState,
                                        onTogglePlayPause = { viewModel.togglePlayPause() },
                                        onPlayerBarClick = { showPlayerPage = true },
                                        onSwipeUp = { showPlayerPage = true },
                                        onPlaylistClick = { showPlaylistSheet = true },
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        hasBottomBar = !ThemeSettings.useFloatingBar,
                                        isFloatingStyle = ThemeSettings.useFloatingBar
                                    )
                                    if (ThemeSettings.useFloatingBar) {
                                        // 悬浮底栏
                                        LiquidGlassNavigationBar {
                                            tabs.forEachIndexed { index, tab ->
                                                LiquidGlassNavigationBarItem(
                                                    selected = selectedTab == index,
                                                    onClick = { selectedTab = index },
                                                    icon = {
                                                        Icon(
                                                            imageVector = icons[index],
                                                            contentDescription = tab
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            text = tab,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        // 普通底栏
                                        NavigationBar(
                                            modifier = Modifier.fillMaxWidth()
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
                                startAction = {
                                    IconButton(
                                        onClick = { shouldScrollToCurrent = true }
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Location,
                                            contentDescription = "定位到当前歌曲",
                                            tint = MiuixTheme.colorScheme.onSurface
                                        )
                                    }
                                },
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
                            }
                            AnimatedContent(
                                targetState = selectedTab,
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
                                        currentScreen = Screen.PlaylistDetail(coverUrl, title)
                                    },
                                    viewModel = viewModel,
                                    isLoggedIn = isLoggedIn,
                                    isPlayerPageVisible = showPlayerPage,
                                    onOpenPlayerPage = { showPlayerPage = true },
                                    modifier = Modifier.padding(padding)
                                )
                                        1 -> PlaylistPage(
                                            recommendState = viewModel.recommendState,
                                            onPlaylistClick = { playlistId, coverUrl, title ->
                                                viewModel.loadPlaylistDetail(playlistId)
                                                currentScreen = Screen.PlaylistDetail(coverUrl, title)
                                            },
                                            onRefresh = { viewModel.loadUserPlaylists() },
                                            isLoggedIn = isLoggedIn,
                                            modifier = Modifier.padding(padding)
                                        )
                                2 -> SettingsPage(
                                    isLoggedIn = isLoggedIn,
                                    onLoginClick = {
                                        currentScreen = Screen.QrLogin
                                    },
                                    onProfileClick = {
                                        currentScreen = Screen.Profile
                                    },
                                    onThemeSettingsClick = {
                                        currentScreen = Screen.ThemeSettings
                                    },
                                    onPlayPageSettingsClick = {
                                        currentScreen = Screen.PlayPageSettings
                                    },
                                    onAboutClick = {
                                        currentScreen = Screen.About
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
                            PlayerBar(
                                playerState = viewModel.playerState,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onPlayerBarClick = { showPlayerPage = true },
                                onSwipeUp = { showPlayerPage = true },
                                onPlaylistClick = { showPlaylistSheet = true },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
                                hasBottomBar = false
                            )
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
                            startAction = {
                                IconButton(
                                    onClick = { shouldScrollToCurrent = true }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Location,
                                        contentDescription = "定位到当前歌曲",
                                        tint = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                            },
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
                        }
                        PlaylistDetailPage(
                            playlistDetail = viewModel.playlistDetailState.detail,
                            isLoading = viewModel.playlistDetailState.isLoading,
                            error = viewModel.playlistDetailState.error,
                            currentTrack = viewModel.playerState.currentTrack,
                            initialCoverUrl = screen.coverUrl,
                            initialTitle = screen.title,
                            isCloudDisk = viewModel.playlistDetailState.detail?.info?.id == "cloud_disk",
                            isLoadingMore = viewModel.isCloudDiskLoadingMore,
                            onLoadMore = { viewModel.loadMoreCloudDiskSongs() },
                            onBackClick = {
                                viewModel.clearPlaylistDetail()
                                currentScreen = Screen.Main
                            },
                            onTrackClick = { track ->
                                // 点击歌曲时替换播放列表
                                val tracks = viewModel.playlistDetailState.detail?.tracks
                                if (tracks != null) {
                                    viewModel.currentPlaylist = tracks
                                }
                                viewModel.playTrack(track)
                            },
                            onPlayAllClick = { tracks ->
                                if (tracks.isNotEmpty()) {
                                    viewModel.currentPlaylist = tracks
                                    viewModel.playTrack(tracks.first())
                                }
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@AnimatedContent,
                            isBackHandlerEnabled = !showPlayerPage,
                            modifier = Modifier.padding(padding)
                        )
                    }
                }
            is Screen.Login -> {
                LoginPage(
                    onBackClick = {
                        currentScreen = Screen.Main
                    },
                    onLoginSuccess = { cookies ->
                        NeteaseApi.setCookie(cookies)
                        isLoggedIn = true
                        currentScreen = Screen.Main
                        viewModel.loadRecommendPlaylists()
                    }
                )
            }
            is Screen.QrLogin -> {
                QrLoginPage(
                    onBackClick = {
                        currentScreen = Screen.Main
                    },
                    onLoginSuccess = { nickname ->
                        isLoggedIn = true
                        currentScreen = Screen.Main
                        viewModel.loadRecommendPlaylists()
                        viewModel.loadUserPlaylists()
                    },
                    context = androidx.compose.ui.platform.LocalContext.current
                )
            }
            is Screen.ThemeSettings -> {
                ThemeSettingsPage(
                    onBackClick = {
                        currentScreen = Screen.Main
                    }
                )
            }
            is Screen.PlayPageSettings -> {
                PlayPageSettingsPage(
                    onBackClick = {
                        currentScreen = Screen.Main
                    }
                )
            }
            is Screen.About -> {
                AboutPage(
                    onBackClick = {
                        currentScreen = Screen.Main
                    },
                    onOpenSourceClick = {
                        // TODO: 实现开放源代码许可页面
                    }
                )
            }
            is Screen.Profile -> {
                ProfilePage(
                    onBackClick = {
                        currentScreen = Screen.Main
                    },
                    onLogout = {
                        isLoggedIn = false
                        currentScreen = Screen.Main
                    },
                    isLoggedIn = isLoggedIn,
                    profileVersion = profileVersion
                )
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
                playerState = viewModel.playerState,
                lyricState = viewModel.lyricState,
                currentPlaylist = viewModel.currentPlaylist,
                isCurrentTrackLiked = viewModel.isCurrentTrackLiked,
                onBackClick = { showPlayerPage = false },
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
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
                        val intent = Intent("miui.intent.action.ACTIVITY_MIPLAY_DETAIL")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Failed to open SystemUI: ${e.message}")
                    }
                },
                onLikeTrack = {
                    viewModel.likeCurrentTrack { isLiked ->
                        val message = if (isLiked) "已收藏" else "已取消收藏"
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onDrawerStateChanged = { isOpen -> isPlayerDrawerOpen = isOpen }
            )
        }
    }
}
}
