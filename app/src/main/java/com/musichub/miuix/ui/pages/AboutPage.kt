package com.musichub.miuix.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musichub.miuix.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.musichub.miuix.ui.FeedBottomPads
import com.musichub.miuix.ui.theme.ThemeSettings
import com.musichub.miuix.viewmodel.MusicPlayerViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun AboutPage(
    onBackClick: () -> Unit,
    onOpenSourceClick: () -> Unit,
    viewModel: MusicPlayerViewModel,
    onCheckUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    var autoCheckUpdate by remember { mutableStateOf(ThemeSettings.autoCheckUpdate) }
    
    // 获取APP版本号
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "关于",
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
            contentPadding = PaddingValues(bottom = FeedBottomPads.None),
            overscrollEffect = null
        ) {
            item { Spacer(modifier = Modifier.height(32.dp)) }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        // 背景层
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // 前景层（放大1.5倍，裁掉多余部分）
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = "App Icon",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = 1.5f,
                                    scaleY = 1.5f
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Music for Miuix",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "v$versionName",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
            item { SmallTitle("版本") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    BasicComponent(
                        title = "打开应用时自动检查更新",
                        endActions = {
                            Switch(
                                checked = autoCheckUpdate,
                                onCheckedChange = { newValue ->
                                    autoCheckUpdate = newValue
                                    ThemeSettings.updateAutoCheckUpdate(newValue)
                                }
                            )
                        },
                        onClick = {
                            autoCheckUpdate = !autoCheckUpdate
                            ThemeSettings.updateAutoCheckUpdate(autoCheckUpdate)
                        }
                    )
                    ArrowPreference(
                        title = "立即检查更新",
                        onClick = onCheckUpdate
                    )
                }
            }
            item { SmallTitle("开源项目引用") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = "Miuix",
                        summary = "使用此项目作为软件UI框架",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/compose-miuix-ui/miuix"))
                            context.startActivity(intent)
                        }
                    )
                    ArrowPreference(
                        title = "BuJuan",
                        summary = "通过此项目实现网易云音乐扫码登录、每日推荐、歌曲播放等功能",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/2697a/bujuan"))
                            context.startActivity(intent)
                        }
                    )
                    ArrowPreference(
                        title = "音理音理!",
                        summary = "通过此项目适配词幕接口",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/cwuom/NeriPlayer"))
                            context.startActivity(intent)
                        }
                    )
                    ArrowPreference(
                        title = "SPlayer-Next",
                        summary = "提供部分网易云相关接口",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SPlayer-Dev/SPlayer-Next"))
                            context.startActivity(intent)
                        }
                    )
                    ArrowPreference(
                        title = "词幕",
                        summary = "提供系统状态栏歌词统一接口",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tomakino/lyricon"))
                            context.startActivity(intent)
                        }
                    )
                    ArrowPreference(
                        title = "LyricInfo",
                        summary = "提供系统媒体播放歌词识别",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/limczhh/LyricInfo"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // 更新日志抽屉
        UpdateLogSheet(viewModel = viewModel)
    }
}
