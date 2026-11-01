package com.hypermusic.miuix.ui.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hypermusic.miuix.data.model.AlbumInfo
import com.hypermusic.miuix.data.model.ArtistInfo
import com.hypermusic.miuix.data.model.RecommendPlaylist
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.data.model.UpdateInfo
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.theme.ThemeSettings
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import com.hypermusic.miuix.viewmodel.RecommendState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecommendPage(
    recommendState: RecommendState,
    onPlaylistClick: (String, String, String) -> Unit,
    viewModel: MusicPlayerViewModel,
    isLoggedIn: Boolean,
    isPlayerPageVisible: Boolean = false,
    onOpenPlayerPage: () -> Unit = {},
    onIntelligenceMode: (String, String) -> Unit = { _, _ -> },
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val isTablet = LocalConfiguration.current.screenWidthDp > 600
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current

    // 下拉刷新回调
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            // 不显示全屏加载态，仅刷新数据（顶部 4 个卡片保持不重建，仅每日推荐封面随数据更新）
            viewModel.loadRecommendPlaylists(showLoading = false)
            viewModel.loadDailyRecommendSongs()
            viewModel.loadUserPlaylists()
            viewModel.loadRadarPlaylists()
            viewModel.loadTopArtists()
            viewModel.loadNewAlbums()
            isRefreshing = false
        }
    }

    val blurBackdrop = rememberPageBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    actions = { com.hypermusic.miuix.ui.components.SearchPlatformActions(onSearchClick, scrollBehavior.state.collapsedFraction >= 0.99f) },
                    title = "推荐",
                    largeTitle = "推荐",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,

            )
            }
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
            modifier = Modifier
                .padding(top = paddingValues.calculateTopPadding())
                .pageLayerBackdrop(blurBackdrop)
        ) {
            if (recommendState.isLoading && !isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    InfiniteProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = 8.dp, bottom = FeedBottomPads.More)
                ) {
                    item { SearchEntry(onSearchClick) }
                    // 0. 新版本提示卡片
                    val update = viewModel.updateState
                    if (update.hasUpdate && !update.dismissed) {
                        item {
                            UpdateAvailableCard(
                                onView = {
                                    viewModel.dismissUpdateCard()
                                    viewModel.openUpdateLog()
                                },
                                onDismiss = { viewModel.dismissUpdateCard() }
                            )
                        }
                    }

                    item { SmallTitle("专属推荐") }

                    // 2. 播放模式（仅卡片，无标题/解释）
                    item {
                        PlayModeRow(
                            dailySongs = recommendState.dailySongs,
                            userPlaylists = recommendState.userPlaylists,
                            isLoggedIn = isLoggedIn,
                            onRandomPlayClick = {
                                if (isLoggedIn) {
                                    viewModel.playRandomOne()
                                    onOpenPlayerPage()
                                } else {
                                    android.widget.Toast.makeText(context, "请登录后使用", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDailyRecommendClick = {
                                onPlaylistClick("daily_recommend", "", "每日推荐")
                            },
                            onFmClick = {
                                if (isLoggedIn) {
                                    if (viewModel.isFMMode) {
                                        android.widget.Toast.makeText(context, "已经在私人FM模式中", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.enterFMMode()
                                        onOpenPlayerPage()
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "请登录后使用", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onIntelligenceModeClick = { trackId, playlistId ->
                                if (isLoggedIn) {
                                    if (viewModel.isIntelligenceMode) {
                                        android.widget.Toast.makeText(context, "已经在心动模式中", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        onIntelligenceMode(trackId, playlistId)
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "请登录后使用", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    // 2. 专属歌单 / 推荐歌单
                    if (recommendState.playlists.isNotEmpty()) {
                        item {
                            val sectionTitle = "专属歌单"
                            if (isTablet) {
                                TabletHomeSection(
                                    title = sectionTitle,
                                    cards = recommendState.playlists
                                ) { playlist, cardModifier ->
                                    TabletCoverCard(
                                        coverUrl = playlist.coverImgUrl,
                                        title = playlist.title,
                                        subtitle = if (playlist.playCount > 0) formatRecommendPlayCount(playlist.playCount) else "",
                                        onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) },
                                        modifier = cardModifier
                                    )
                                }
                            } else {
                                HomeSection(title = sectionTitle) {
                                    items(recommendState.playlists) { playlist ->
                                        CoverCard(
                                            coverUrl = playlist.coverImgUrl,
                                            title = playlist.title,
                                            subtitle = if (playlist.playCount > 0) formatRecommendPlayCount(playlist.playCount) else "",
                                            onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. 雷达歌单
                    if (recommendState.radarPlaylists.isNotEmpty()) {
                        item {
                            if (isTablet) {
                                TabletHomeSection(
                                    title = "雷达歌单",
                                    cards = recommendState.radarPlaylists
                                ) { playlist, cardModifier ->
                                    val displayTitle = if (playlist.title.contains("|")) {
                                        playlist.title.substringAfterLast("|").trim()
                                    } else {
                                        playlist.title
                                    }
                                    TabletCoverCard(
                                        coverUrl = playlist.coverImgUrl,
                                        title = displayTitle,
                                        subtitle = if (playlist.playCount > 0) formatRecommendPlayCount(playlist.playCount) else "",
                                        onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, displayTitle) },
                                        modifier = cardModifier
                                    )
                                }
                            } else {
                                HomeSection(
                                    title = "雷达歌单"
                                ) {
                                    items(recommendState.radarPlaylists) { playlist ->
                                        // 「私人雷达」等个性化歌单：首页只显示基础名称（如「私人雷达」），点进去再看完整歌单
                                        val displayTitle = if (playlist.title.contains("|")) {
                                            playlist.title.substringAfterLast("|").trim()
                                        } else {
                                            playlist.title
                                        }
                                        CoverCard(
                                            coverUrl = playlist.coverImgUrl,
                                            title = displayTitle,
                                            subtitle = if (playlist.playCount > 0) formatRecommendPlayCount(playlist.playCount) else "",
                                            onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, displayTitle) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. 新碟上架
                    if (recommendState.newAlbums.isNotEmpty()) {
                        item {
                            if (isTablet) {
                                TabletHomeSection(
                                    title = "新碟上架",
                                    cards = recommendState.newAlbums
                                ) { album, cardModifier ->
                                    TabletCoverCard(
                                        coverUrl = album.picUrl,
                                        title = album.name,
                                        subtitle = album.artist,
                                        onClick = { if (album.id > 0) onAlbumClick(album.id) },
                                        modifier = cardModifier
                                    )
                                }
                            } else {
                                HomeSection(
                                    title = "新碟上架"
                                ) {
                                    items(recommendState.newAlbums) { album ->
                                        AlbumCard(
                                            album = album,
                                            onClick = { if (album.id > 0) onAlbumClick(album.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }

        // 更新日志抽屉
        UpdateLogSheet(viewModel = viewModel)
    }
}

/**
 * 首页板块：标准小标题和一行横向卡片
 */
@Composable
internal fun HomeSection(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(title)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            content()
        }
    }
}

/**
 * 平板首页板块：两行卡片，按列横向滚动
 */
@Composable
private fun <T> TabletHomeSection(
    title: String,
    cards: List<T>,
    card: @Composable (T, Modifier) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(title)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            val cardWidth = (maxWidth / 3.2f).coerceIn(220.dp, 300.dp)
            val columns = (cards.size + 1) / 2
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                items(columns) { columnIndex ->
                    val top = cards[columnIndex]
                    val bottom = cards.getOrNull(columnIndex + columns)
                    Column(
                        modifier = Modifier.width(cardWidth),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        card(top, Modifier.fillMaxWidth())
                        if (bottom != null) {
                            card(bottom, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/**
 * 平板首页横向卡片：左封面，右信息
 */
@Composable
private fun TabletCoverCard(
    coverUrl: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_menu_gallery)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 新版本提示卡片
 */
@Composable
private fun UpdateAvailableCard(
    onView: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onView
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "检测到新版本，点击查看",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = "关闭",
                    tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 封面卡片：上图下标题、解释（首页通用卡片样式）
 */
@Composable
internal fun CoverCard(
    coverUrl: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(156.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentScale = ContentScale.Crop,
            error = painterResource(android.R.drawable.ic_menu_gallery)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 歌手卡片：圆形头像 + 名称
 */
@Composable
private fun ArtistCard(
    artist: ArtistInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(136.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = artist.picUrl,
                contentDescription = artist.name,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_menu_gallery)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = artist.name,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 新碟卡片：方形封面 + 专辑名 + 歌手
 */
@Composable
private fun AlbumCard(
    album: AlbumInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(156.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        AsyncImage(
            model = album.picUrl,
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentScale = ContentScale.Crop,
            error = painterResource(android.R.drawable.ic_menu_gallery)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = album.name,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.artist,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 播放模式：随机播放 / 每日推荐 / 私人FM / 心动模式
 */
@Composable
private fun PlayModeRow(
    dailySongs: List<Track>,
    userPlaylists: List<RecommendPlaylist>,
    isLoggedIn: Boolean,
    onRandomPlayClick: () -> Unit,
    onDailyRecommendClick: () -> Unit,
    onFmClick: () -> Unit,
    onIntelligenceModeClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val likedPlaylistId = userPlaylists.firstOrNull { it.title.contains("喜欢") }?.id
        ?: userPlaylists.firstOrNull()?.id
        ?: ""
    val heartSeedId = dailySongs.firstOrNull()?.id ?: ""

    val dailyCover = dailySongs.firstOrNull()?.imgUrl
    // 每日推荐始终使用「浅色模式」的主题色（不受深色模式影响）
    val dailyColor = run {
        val lightMode = if (ThemeSettings.followMonet) {
            top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetLight
        } else {
            top.yukonga.miuix.kmp.theme.ColorSchemeMode.Light
        }
        val controller = remember(lightMode) { top.yukonga.miuix.kmp.theme.ThemeController(lightMode) }
        controller.currentColors().primary
    }
    val randomColor = Color(0xFFB88B64)
    val fmColor = Color(0xFF7D4F2A)
    val heartColor = Color(0xFF6B2D38)

    LazyRow(
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        item {
            PlayModeCard(
                title = "每日推荐",
                subtitle = "每天为你精选",
                gradientColor = dailyColor,
                onClick = onDailyRecommendClick,
                backgroundCoverUrl = dailyCover
            ) {
                if (!dailyCover.isNullOrEmpty()) {
                    AsyncImage(
                        model = dailyCover,
                        contentDescription = "每日推荐",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    GradientCover(
                        brush = Brush.verticalGradient(listOf(Color(0xFFFFB74D), Color(0xFFF57C00))),
                        emoji = "📅"
                    )
                }
            }
        }
        item {
            PlayModeCard(
                title = "随机播放",
                subtitle = "试试手气",
                gradientColor = randomColor,
                onClick = onRandomPlayClick
            ) {
                Image(
                    painter = painterResource(com.hypermusic.miuix.R.drawable.random_play),
                    contentDescription = "随机播放",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        item {
            PlayModeCard(
                title = "私人FM",
                subtitle = "为你不停推荐",
                gradientColor = fmColor,
                onClick = onFmClick
            ) {
                Image(
                    painter = painterResource(com.hypermusic.miuix.R.drawable.personal_fm),
                    contentDescription = "私人FM",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        item {
            PlayModeCard(
                title = "心动模式",
                subtitle = "根据喜好智能播放",
                gradientColor = heartColor,
                onClick = { onIntelligenceModeClick(heartSeedId, likedPlaylistId) }
            ) {
                Image(
                    painter = painterResource(com.hypermusic.miuix.R.drawable.xindong_mode),
                    contentDescription = "心动模式",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
internal fun PlayModeCard(
    title: String,
    subtitle: String,
    gradientColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundCoverUrl: String? = null,
    cover: @Composable () -> Unit
) {
    val onColor = Color.White
    Card(
        modifier = modifier.width(210.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 每日推荐：用封面放大裁切、模糊、压暗作为整张卡片背景
            if (!backgroundCoverUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = backgroundCoverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(40.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = 0.5f)
                                )
                            )
                        )
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    if (backgroundCoverUrl.isNullOrEmpty()) {
                        cover()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.8f to Color.Transparent,
                                        1f to gradientColor
                                    )
                                )
                        )
                    } else {
                        // 封面底部渐隐：0.8 处不透明 → 1.0 处完全透明，露出模糊背景
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0.8f to Color.Black,
                                            1f to Color.Transparent
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        ) {
                            cover()
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (backgroundCoverUrl.isNullOrEmpty()) {
                                Modifier.background(gradientColor)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = onColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MiuixTheme.textStyles.footnote2,
                            color = onColor.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientCover(brush: Brush, emoji: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 48.sp
        )
    }
}

internal fun formatRecommendPlayCount(count: Long): String {
    return when {
        count >= 100_000_000 -> "%.1f亿播放".format(count / 100_000_000.0)
        count >= 10_000 -> "%.1f万播放".format(count / 10_000.0)
        else -> "$count 播放"
    }
}
