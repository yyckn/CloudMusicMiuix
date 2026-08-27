package com.cloudmusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun AboutPage(
    onBackClick: () -> Unit,
    onOpenSourceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()

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
            contentPadding = PaddingValues(bottom = 16.dp),
            overscrollEffect = null
        ) {
            item { Spacer(modifier = Modifier.height(32.dp)) }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = "https://p1.music.126.net/z6i7sKp3e5q3e5q3e5q3eQ==/109951166968412345.jpg",
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(android.R.drawable.ic_menu_gallery)
                    )
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
                            text = "CloudMusic Miuix",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "v0.5.1-dev",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
            item { SmallTitle("其他") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = "开放源代码许可",
                        summary = "查看开源组件许可信息",
                        onClick = onOpenSourceClick
                    )
                }
            }
        }
    }
}
