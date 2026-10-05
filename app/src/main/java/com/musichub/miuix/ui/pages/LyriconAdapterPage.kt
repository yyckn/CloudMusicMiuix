package com.musichub.miuix.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.musichub.miuix.R
import com.musichub.miuix.ui.FeedBottomPads
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun LyriconAdapterPage(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("lyricon_prefs", android.content.Context.MODE_PRIVATE)
    }
    var pushLyricsToLyricon by remember {
        mutableStateOf(prefs.getBoolean("push_lyrics_enabled", true))
    }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "歌词接口适配",
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onBackClick() }
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(top = paddingValues.calculateTopPadding())
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = FeedBottomPads.None)
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    // 词幕
                    BasicComponent(
                        title = "词幕",
                        summary = "基于 Xposed / LSPosed 的 Android 状态栏歌词工具，将正在播放的歌词实时显示在系统状态栏。",
                        startAction = {
                            Image(
                                painter = painterResource(R.drawable.lyricon),
                                contentDescription = "词幕",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                        },
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tomakino/lyricon"))
                            context.startActivity(intent)
                        }
                    )
                    // 推送歌词到词幕
                    SwitchPreference(
                        checked = pushLyricsToLyricon,
                        onCheckedChange = { enabled ->
                            pushLyricsToLyricon = enabled
                            prefs.edit().putBoolean("push_lyrics_enabled", enabled).apply()
                            com.musichub.miuix.service.LyriconProviderManager.pushLyricsEnabled = enabled
                        },
                        title = "推送歌词到词幕",
                        summary = if (pushLyricsToLyricon) "已开启" else "已关闭"
                    )
                    // LyricInfo
                    ArrowPreference(
                        title = "LyricInfo",
                        summary = "基于 libxposed API 102 的 Xposed/LSPosed 模块，针对不同音乐应用获取整首歌词并注入到 MediaMetadata.extras.lyricInfo 字段。",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/limczhh/LyricInfo"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
