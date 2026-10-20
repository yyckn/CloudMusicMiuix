package com.musichub.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.musichub.miuix.ui.components.BlurredBar
import com.musichub.miuix.ui.components.pageLayerBackdrop
import com.musichub.miuix.ui.components.rememberPageBlurBackdrop
import coil.compose.AsyncImage
import com.musichub.miuix.R
import com.musichub.miuix.data.api.NeteaseApi
import com.musichub.miuix.data.model.Comment
import com.musichub.miuix.data.model.CommentResult
import com.musichub.miuix.ui.FeedBottomPads
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommentPage(
    songId: String,
    songTitle: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var totalComments by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var offset by remember { mutableIntStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }

    // 展开的回复
    var expandedReplies by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var replyLoading by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var repliesMap by remember { mutableStateOf<Map<Long, List<Comment>>>(emptyMap()) }

    val tabTitles = listOf("热门", "最新")
    val sortTypes = listOf(2, 3) // 2: 热度, 3: 最新

    BackHandler {
        onBackClick()
    }

    // 加载评论
    suspend fun loadComments() {
        isLoading = true
        offset = 0
        comments = emptyList()
        expandedReplies = emptySet()
        repliesMap = emptyMap()

        NeteaseApi.getSongComments(songId, offset = 0, limit = 20, sortType = sortTypes[selectedTab]).fold(
            onSuccess = { result ->
                comments = result.comments
                totalComments = result.total
                hasMore = result.hasMore
                offset = result.comments.size
                isLoading = false
            },
            onFailure = {
                isLoading = false
            }
        )
    }

    // 初始加载
    LaunchedEffect(selectedTab) {
        loadComments()
    }

    // 下拉刷新
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            loadComments()
            isRefreshing = false
        }
    }

    val blurBackdrop = rememberPageBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = "歌曲评论",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                // TabRow
                val tabTitles = listOf("热门", "最新")
                TabRow(
                    tabs = tabTitles,
                    selectedTabIndex = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (comments.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无评论",
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = FeedBottomPads.Standard
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(comments) { index, comment ->
                            // 加载更多
                            if (index >= comments.size - 3 && hasMore && !isLoadingMore) {
                                LaunchedEffect(Unit) {
                                    isLoadingMore = true
                                    NeteaseApi.getSongComments(songId, offset = offset, limit = 20, sortType = sortTypes[selectedTab]).fold(
                                        onSuccess = { result ->
                                            comments = comments + result.comments
                                            hasMore = result.hasMore
                                            offset += result.comments.size
                                            isLoadingMore = false
                                        },
                                        onFailure = {
                                            isLoadingMore = false
                                    }
                                )
                            }
                        }

                        CommentItem(
                            comment = comment,
                            isExpanded = expandedReplies.contains(comment.commentId),
                            isLoadingReplies = replyLoading.contains(comment.commentId),
                            replies = repliesMap[comment.commentId] ?: emptyList(),
                            onToggleReplies = {
                                if (expandedReplies.contains(comment.commentId)) {
                                    expandedReplies = expandedReplies - comment.commentId
                                } else {
                                    // 加载回复
                                    if (!repliesMap.containsKey(comment.commentId)) {
                                        replyLoading = replyLoading + comment.commentId
                                        coroutineScope.launch {
                                            NeteaseApi.getCommentReplies(comment.commentId, songId).fold(
                                                onSuccess = { replies ->
                                                    repliesMap = repliesMap + (comment.commentId to replies)
                                                    expandedReplies = expandedReplies + comment.commentId
                                                    replyLoading = replyLoading - comment.commentId
                                                },
                                                onFailure = {
                                                    replyLoading = replyLoading - comment.commentId
                                                }
                                            )
                                        }
                                    } else {
                                        expandedReplies = expandedReplies + comment.commentId
                                    }
                                }
                            }
                        )
                    }

                    // 加载更多指示器
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    isExpanded: Boolean,
    isLoadingReplies: Boolean,
    replies: List<Comment>,
    onToggleReplies: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 用户信息行
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                AsyncImage(
                    model = comment.avatarUrl,
                    contentDescription = comment.nickname,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.ic_launcher_foreground)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    // 昵称
                    Text(
                        text = comment.nickname,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 时间
                    Text(
                        text = formatCommentTime(comment.time),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // 评论内容
            Text(
                text = comment.content,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
                lineHeight = 20.sp
            )

            // 展开/收起回复按钮
            if (comment.replies.isNotEmpty() || replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isExpanded) "收起回复" else "展开${comment.replies.size}条回复",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onToggleReplies() }
                        .padding(vertical = 4.dp)
                )
            }

            // 回复列表
            if (isExpanded && replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                replies.forEach { reply ->
                    ReplyItem(reply = reply)
                }
            }

            // 加载中指示器
            if (isLoadingReplies) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun ReplyItem(
    reply: Comment,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 36.dp, top = 8.dp), // 左侧增加边距
        verticalAlignment = Alignment.Top
    ) {
        // 回复头像
        AsyncImage(
            model = reply.avatarUrl,
            contentDescription = reply.nickname,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.ic_launcher_foreground)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            // 昵称和时间
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = reply.nickname,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatCommentTime(reply.time),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            // 回复内容
            Text(
                text = reply.content,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
                lineHeight = 18.sp
            )
        }
    }
}

private fun formatCommentTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 30 * 86400_000 -> "${diff / 86400_000}天前"
        else -> {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
