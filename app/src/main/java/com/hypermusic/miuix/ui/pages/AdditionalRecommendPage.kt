package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.hypermusic.miuix.ui.components.AsyncImage
import com.hypermusic.miuix.data.api.*
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
internal fun AdditionalRecommendPage(platform: MusicPlatform, recommendations: AdditionalRecommendations,
    listState: LazyListState, onSearch: () -> Unit, showSearchAction: Boolean,
    onPlaylist: (String, String, String) -> Unit, onRadio: (String) -> Unit) {
    val backdrop = rememberPageBlurBackdrop()
    val behavior = MiuixScrollBehavior()
    val refresh = rememberPullToRefreshState()
    var refreshRevision by remember(platform) { mutableIntStateOf(0) }
    var handledRefreshRevision by remember(platform) { mutableIntStateOf(0) }
    val accountRevision = AdditionalAccounts.revision.intValue
    val loading = recommendations.loading[platform] == true
    val home = recommendations.homes[platform] ?: AdditionalRecommendations.placeholderHome(platform)
    val tabActive = LocalMainTabActive.current
    val active = tabActive && LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(platform, accountRevision, refreshRevision, tabActive) {
        if (!tabActive) return@LaunchedEffect
        val force = refreshRevision != handledRefreshRevision
        handledRefreshRevision = refreshRevision
        recommendations.loadHome(platform, force = force)
    }
    fun open(card: RecommendationCard) {
        if (card.radio) onRadio(card.id) else onPlaylist(card.id, card.cover, card.title)
    }
    Scaffold(topBar = { BlurredBar(backdrop = backdrop) {
        TopAppBar(title = "推荐", largeTitle = "推荐", scrollBehavior = behavior, defaultWindowInsetsPadding = false,
            actions = { SearchPlatformActions(onSearch, showSearchAction && behavior.state.collapsedFraction > .99f) })
    } }) { padding ->
        PullToRefresh(isRefreshing = loading, onRefresh = { refreshRevision++ }, pullToRefreshState = refresh,
            contentPadding = PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding())),
            topAppBarScrollBehavior = behavior, refreshTexts = listOf("继续下拉以刷新", "松手后刷新", "正在刷新...", "刷新成功"),
            modifier = Modifier.padding(top = pageViewportTopInset(padding.calculateTopPadding())).pageLayerBackdrop(backdrop)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().overScrollVertical()
                .nestedScroll(behavior.nestedScrollConnection), contentPadding = PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding()), bottom = FeedBottomPads.More), overscrollEffect = null) {
                home.error?.let { message -> item { Text(message, Modifier.padding(16.dp), color = MiuixTheme.colorScheme.error) } }
                if (home.large.isNotEmpty()) {
                    item { SmallTitle("专属推荐") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                            items(home.large, key = { it.id }) { card ->
                                PlayModeCard(title = card.title, subtitle = card.subtitle,
                                    gradientColor = MiuixTheme.colorScheme.primary, onClick = { open(card) },
                                    backgroundCoverUrl = card.cover.takeIf { card.background == "cover" },
                                    plainLoadingBackground = card.background == "cover",
                                    fanGradientBackground = card.background == "fm", curveGradientBackground = card.background == "heart",
                                    randomGradientBackground = card.background == "random", randomBackgroundActive = active) {
                                    RecommendationCover(card)
                                }
                            }
                        }
                    }
                }
                home.sections.filter { it.cards.isNotEmpty() }.forEach { section ->
                    item {
                        HomeSection(title = section.title, lastCardTitle = section.cards.lastOrNull()?.title) {
                            items(section.cards, key = { it.id }) { card ->
                                CoverCard(card.cover, card.title, card.subtitle, onClick = { open(card) }, modifier = Modifier.animateItem(),
                                    imageScale = if (platform == MusicPlatform.QISHUI) .5f else 1f,
                                    sharedCoverKey = if (card.radio) null else playlistCoverKey(card.id))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationCover(card: RecommendationCard) {
    if (card.cover.isNotBlank()) {
        AsyncImage(imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(LocalContext.current,
            com.hypermusic.miuix.data.cache.ImageCaches.Kind.COVER), model = card.cover, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
    } else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
        MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.primary.copy(alpha = .35f)))))
}
