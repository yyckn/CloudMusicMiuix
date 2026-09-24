package com.cloudmusic.miuix.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 信息流（LazyColumn 等）底部空白三档。
 *
 * - [None]：设置二级页，底部无播放器
 * - [Standard]：底部有迷你播放器（歌单详情、歌手、评论、专辑）
 * - [More]：迷你播放器 + 底部导航（推荐、收藏、设置主页）
 */
object FeedBottomPads {
    /** 无垫高 */
    val None: Dp = 16.dp

    /** 标准垫高（迷你播放器） */
    val Standard: Dp = 94.dp

    /** 更多垫高（迷你播放器 + 导航栏） */
    val More: Dp = 168.dp
}
