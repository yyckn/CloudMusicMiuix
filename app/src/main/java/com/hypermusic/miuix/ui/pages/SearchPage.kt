package com.hypermusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.data.api.PlatformManager
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.*
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SearchEntry(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        SearchBar(inputField = {
            InputField(query = "", onQueryChange = {}, onSearch = {}, expanded = false,
                onExpandedChange = { if (it) onClick() }, label = "搜索音乐")
        }, expanded = false, onExpandedChange = {}) {}
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
fun SearchPage(viewModel: MusicPlayerViewModel, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val prefs = remember { context.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE) }
    var history by remember { mutableStateOf(prefs.getStringSet("history", emptySet()).orEmpty().toList()) }
    fun search(text: String) {
        val keyword = text.trim()
        if (keyword.isEmpty()) return
        query = keyword
        submitted = true
        history = (listOf(keyword) + history.filterNot { it == keyword }).take(30)
        prefs.edit().putStringSet("history", history.toSet()).apply()
        focusManager.clearFocus()
        viewModel.searchSongs(keyword)
    }
    val platform = PlatformManager.current
    val state = viewModel.searchState
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBlurBackdrop()
    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(platform) {
        viewModel.clearSearch()
        if (submitted && query.isNotBlank()) viewModel.searchSongs(query)
    }
    Scaffold(topBar = {
        BlurredBar(backdrop = backdrop) {
            TopAppBar(title = "搜索", largeTitle = "搜索", scrollBehavior = scrollBehavior,
                actions = { PlatformSwitch() }, defaultWindowInsetsPadding = false,
                navigationIcon = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                        modifier = Modifier.padding(start = 8.dp).clickable { onBack() })
                },
                color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface)
        }
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
            .overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection).pageLayerBackdrop(backdrop),
            contentPadding = PaddingValues(bottom = FeedBottomPads.None), overscrollEffect = null) {
            item {
                SearchBar(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), inputField = {
                    InputField(modifier = Modifier.focusRequester(focusRequester), query = query, onQueryChange = {
                        query = it
                        if (it.isEmpty()) { submitted = false; viewModel.clearSearch() }
                    }, onSearch = {
                        search(query)
                    }, expanded = false, onExpandedChange = {}, label = "搜索音乐")
                }, expanded = false, onExpandedChange = {}) {}
            }
            if (state.isLoading) item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { InfiniteProgressIndicator() }
            } else if (state.error != null) item {
                Text("搜索失败：${state.error}", modifier = Modifier.padding(16.dp))
            } else if (state.results.isNotEmpty()) {
                items(state.results) { track ->
                    TrackItem(track = track, isPlaying = track.id == viewModel.playerState.currentTrack?.id,
                        onClick = { viewModel.playTrackWithMode(track, state.results) })
                }
            } else if (submitted) item { Text("没有找到歌曲", modifier = Modifier.padding(16.dp)) }
            else if (history.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("搜索历史")
                        Text("清除", modifier = Modifier.clickable { history = emptyList(); prefs.edit().remove("history").apply() },
                            color = MiuixTheme.colorScheme.primary)
                    }
                }
                items(history) { word ->
                    BasicComponent(title = word, onClick = { search(word) })
                }
            } else item { Text("输入关键词搜索音乐", modifier = Modifier.padding(16.dp)) }
        }
    }
}
