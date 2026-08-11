package com.cloudmusic.miuix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudmusic.miuix.data.api.NeteaseApi
import com.cloudmusic.miuix.ui.components.PlayerBar
import com.cloudmusic.miuix.ui.pages.LoginPage
import com.cloudmusic.miuix.ui.pages.MyPage
import com.cloudmusic.miuix.ui.pages.PlaylistDetailPage
import com.cloudmusic.miuix.ui.pages.PlaylistPage
import com.cloudmusic.miuix.ui.pages.RecommendPage
import com.cloudmusic.miuix.ui.theme.CloudMusicMiuixTheme
import com.cloudmusic.miuix.viewmodel.MusicPlayerViewModel
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold

sealed class Screen {
    object Main : Screen()
    object PlaylistDetail : Screen()
    object Login : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NeteaseApi.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            CloudMusicMiuixTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MusicPlayerViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var isLoggedIn by remember { mutableStateOf(NeteaseApi.isLoggedIn()) }
    val tabs = listOf("推荐", "歌单", "我的")
    val icons = listOf(Icons.Default.Home, Icons.Default.List, Icons.Default.Person)

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
                else -> slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
            }
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            is Screen.Main -> {
                Scaffold(
                    bottomBar = {
                        Column {
                            PlayerBar(
                                playerState = viewModel.playerState,
                                onTogglePlayPause = { viewModel.togglePlayPause() }
                            )
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
                ) { padding ->
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
                                onPlaylistClick = { playlistId ->
                                    viewModel.loadPlaylistDetail(playlistId)
                                    currentScreen = Screen.PlaylistDetail
                                },
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
                            2 -> MyPage(
                                isLoggedIn = isLoggedIn,
                                onLoginClick = {
                                    currentScreen = Screen.Login
                                },
                                modifier = Modifier.padding(padding)
                            )
                        }
                    }
                }
            }
            is Screen.PlaylistDetail -> {
                Scaffold(
                    bottomBar = {
                        PlayerBar(
                            playerState = viewModel.playerState,
                            onTogglePlayPause = { viewModel.togglePlayPause() }
                        )
                    }
                ) { padding ->
                    PlaylistDetailPage(
                        playlistDetail = viewModel.playlistDetailState.detail,
                        isLoading = viewModel.playlistDetailState.isLoading,
                        error = viewModel.playlistDetailState.error,
                        currentTrack = viewModel.playerState.currentTrack,
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
        }
    }
}
