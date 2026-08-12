package com.cloudmusic.miuix

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudmusic.miuix.data.api.NeteaseApi
import com.cloudmusic.miuix.ui.components.PlayerBar
import com.cloudmusic.miuix.ui.pages.AboutPage
import com.cloudmusic.miuix.ui.pages.LoginPage
import com.cloudmusic.miuix.ui.pages.PlayerPage
import com.cloudmusic.miuix.ui.pages.PlaylistDetailPage
import com.cloudmusic.miuix.ui.pages.PlaylistPage
import com.cloudmusic.miuix.ui.pages.QrLoginPage
import com.cloudmusic.miuix.ui.pages.RecommendPage
import com.cloudmusic.miuix.ui.pages.SettingsPage
import com.cloudmusic.miuix.ui.pages.ThemeSettingsPage
import com.cloudmusic.miuix.ui.pages.TrackItem
import com.cloudmusic.miuix.ui.components.LiquidGlassNavigationBar
import com.cloudmusic.miuix.ui.components.LiquidGlassNavigationBarItem
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
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
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
    object About : Screen()
    object Player : Screen()
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
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val tabs = listOf("推荐", "歌单", "设置")
    val icons = listOf(MiuixIcons.Home, MiuixIcons.ListView, MiuixIcons.Settings)
    val railState = rememberNavigationRailState()
    val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    SharedTransitionLayout {
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
                initialState is Screen.Main && targetState is Screen.About ->
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                initialState is Screen.About && targetState is Screen.Main ->
                    slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                initialState is Screen.Main && targetState is Screen.Player ->
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                initialState is Screen.Player && targetState is Screen.Main ->
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
                                    Column {
                                        PlayerBar(
                                            playerState = viewModel.playerState,
                                            onTogglePlayPause = { viewModel.togglePlayPause() },
                                            onPlayerBarClick = { currentScreen = Screen.Player },
                                            onPlaylistClick = { showPlaylistSheet = true },
                                            sharedTransitionScope = this@SharedTransitionLayout,
                                            animatedVisibilityScope = this@AnimatedContent
                                        )
                                    }
                                }
                            ) { padding ->
                                // 播放列表抽屉
                                OverlayBottomSheet(
                                    show = showPlaylistSheet,
                                    title = "播放列表",
                                    insideMargin = DpSize(12.dp, 0.dp),
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
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            items(viewModel.currentPlaylist) { track ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
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
                                            sharedTransitionScope = this@SharedTransitionLayout,
                                            animatedVisibilityScope = this@AnimatedContent,
                                            modifier = Modifier.padding(padding)
                                        )
                                        1 -> PlaylistPage(
                                            tracks = viewModel.currentPlaylist,
                                            currentTrack = viewModel.playerState.currentTrack,
                                            onTrackClick = { track ->
                                                viewModel.playTrack(track)
                                            },
                                            modifier = Modifier.padding(padding)
                                        )
                                2 -> SettingsPage(
                                    isLoggedIn = isLoggedIn,
                                    onLoginClick = {
                                        currentScreen = Screen.Login
                                    },
                                    onThemeSettingsClick = {
                                        currentScreen = Screen.ThemeSettings
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
                                        onPlayerBarClick = { currentScreen = Screen.Player },
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
                            OverlayBottomSheet(
                                show = showPlaylistSheet,
                                title = "播放列表",
                                insideMargin = DpSize(12.dp, 0.dp),
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
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        items(viewModel.currentPlaylist) { track ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
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
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    modifier = Modifier.padding(padding)
                                )
                                    1 -> PlaylistPage(
                                        tracks = viewModel.currentPlaylist,
                                        currentTrack = viewModel.playerState.currentTrack,
                                        onTrackClick = { track ->
                                            viewModel.playTrack(track)
                                        },
                                        modifier = Modifier.padding(padding)
                                    )
                                    2 -> SettingsPage(
                                        isLoggedIn = isLoggedIn,
                                        onLoginClick = {
                                            currentScreen = Screen.QrLogin
                                        },
                                        onThemeSettingsClick = {
                                            currentScreen = Screen.ThemeSettings
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
                                onPlayerBarClick = { currentScreen = Screen.Player },
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
                                hasBottomBar = false
                            )
                        }
                    ) { padding ->
                        PlaylistDetailPage(
                            playlistDetail = viewModel.playlistDetailState.detail,
                            isLoading = viewModel.playlistDetailState.isLoading,
                            error = viewModel.playlistDetailState.error,
                            currentTrack = viewModel.playerState.currentTrack,
                            initialCoverUrl = screen.coverUrl,
                            initialTitle = screen.title,
                            onBackClick = {
                                viewModel.clearPlaylistDetail()
                                currentScreen = Screen.Main
                            },
                            onTrackClick = { track ->
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
            is Screen.Player -> {
                PlayerPage(
                    playerState = viewModel.playerState,
                    onBackClick = {
                        currentScreen = Screen.Main
                    },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onPrevious = { viewModel.playPrevious() },
                    onNext = { viewModel.playNext() }
                )
            }
        }
    }
}
}
