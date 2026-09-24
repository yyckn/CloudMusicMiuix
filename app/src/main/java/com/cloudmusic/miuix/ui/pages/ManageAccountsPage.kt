package com.cloudmusic.miuix.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.cloudmusic.miuix.ui.FeedBottomPads
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

data class AccountInfo(
    val userId: String,
    val nickname: String,
    val avatarUrl: String
)

@Composable
fun ManageAccountsPage(
    accounts: List<AccountInfo>,
    onBackClick: () -> Unit,
    onAccountClick: (String) -> Unit,
    onLoginNewAccount: () -> Unit,
    playbackAccountIndex: Int = 0,
    dataAccountIndex: Int = 0,
    onPlaybackAccountChange: (Int) -> Unit = {},
    onDataAccountChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    
    // 构建账号显示列表
    val accountOptions = accounts.map { "${it.nickname}（ID：${it.userId}）" }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "管理多账号",
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
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = FeedBottomPads.None)
        ) {
            // 账号管理
            item { SmallTitle("账号管理") }
            
            // 账号列表
            accounts.forEach { account ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        onClick = { onAccountClick(account.userId) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = account.avatarUrl,
                                contentDescription = account.nickname,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(24.dp)),
                                contentScale = ContentScale.Crop,
                                error = painterResource(android.R.drawable.ic_menu_gallery)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.nickname,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ID: ${account.userId}",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
            
            // 登录新账号按钮
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onLoginNewAccount,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(text = "登录新账号")
                }
            }
            
            // 账号切换（仅在2个以上账号时显示）
            if (accounts.size >= 2) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SmallTitle("账号切换")
                }
                
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        WindowDropdownPreference(
                            title = "播放歌曲",
                            summary = "需登录带有VIP账号后才能播放部分版权歌曲",
                            items = accountOptions,
                            selectedIndex = playbackAccountIndex,
                            onSelectedIndexChange = onPlaybackAccountChange
                        )
                        WindowDropdownPreference(
                            title = "获取数据",
                            summary = "个人歌单、每日推荐、收藏等",
                            items = accountOptions,
                            selectedIndex = dataAccountIndex,
                            onSelectedIndexChange = onDataAccountChange
                        )
                    }
                }
            }
        }
    }
}
