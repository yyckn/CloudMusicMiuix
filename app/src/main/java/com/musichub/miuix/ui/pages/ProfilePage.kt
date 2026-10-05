package com.musichub.miuix.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musichub.miuix.data.api.NeteaseApi
import com.musichub.miuix.data.model.UserProfile
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.musichub.miuix.ui.FeedBottomPads
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.unit.DpSize

@Composable
fun ProfilePage(
    onBackClick: () -> Unit,
    onLogout: () -> Unit,
    isLoggedIn: Boolean = true,
    profileVersion: Int = 0,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showExportWarning by remember { mutableStateOf(false) }
    var exportType by remember { mutableStateOf("") } // "clipboard" or "file"
    var exportButtonEnabled by remember { mutableStateOf(false) }
    var exportCountdown by remember { mutableIntStateOf(3) }
    val pullToRefreshState = rememberPullToRefreshState()

    // SAF文件选择器
    val fileSaverLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                val cookies = NeteaseApi.getCookie()
                val jsonCredentials = formatCookiesToJson(cookies)
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(jsonCredentials.toByteArray())
                }
                android.widget.Toast.makeText(context, "保存成功", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "保存失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler {
        onBackClick()
    }

    // 导出警告对话框倒计时
    LaunchedEffect(showExportWarning) {
        if (showExportWarning) {
            exportButtonEnabled = false
            exportCountdown = 3
            for (i in 3 downTo 1) {
                exportCountdown = i
                delay(1000)
            }
            exportButtonEnabled = true
        }
    }

    // 加载用户信息（当isLoggedIn或profileVersion变化时重新加载）
    LaunchedEffect(isLoggedIn, profileVersion) {
        android.util.Log.d("ProfilePage", "LaunchedEffect triggered: isLoggedIn=$isLoggedIn, profileVersion=$profileVersion")
        if (!isLoggedIn) {
            android.util.Log.d("ProfilePage", "Not logged in, clearing profile")
            userProfile = null
            isLoading = false
            return@LaunchedEffect
        }
        android.util.Log.d("ProfilePage", "Loading user profile from API...")
        NeteaseApi.getUserProfile().fold(
            onSuccess = { profile ->
                android.util.Log.d("ProfilePage", "Profile loaded from API: nickname=${profile.nickname}, userId=${profile.userId}")
                userProfile = profile
                isLoading = false
                android.util.Log.d("ProfilePage", "isLoading set to false")
            },
            onFailure = { e ->
                android.util.Log.e("ProfilePage", "Failed to load profile: ${e.message}")
                // 如果API失败，尝试从缓存加载
                val cachedProfile = NeteaseApi.getUserProfileFromCache()
                if (cachedProfile != null) {
                    android.util.Log.d("ProfilePage", "Using cached profile: ${cachedProfile.nickname}")
                    userProfile = cachedProfile
                }
                isLoading = false
                android.util.Log.d("ProfilePage", "isLoading set to false (from cache)")
            }
        )
    }

    // 下拉刷新回调（手动刷新才请求API）
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            android.util.Log.d("ProfilePage", "Refreshing user profile from API...")
            NeteaseApi.getUserProfile().fold(
                onSuccess = { profile ->
                    android.util.Log.d("ProfilePage", "Profile refreshed: ${profile.nickname}")
                    userProfile = profile
                },
                onFailure = { e ->
                    android.util.Log.e("ProfilePage", "Failed to refresh profile: ${e.message}")
                }
            )
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "我的资料",
                largeTitle = "我的资料",
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
                },
                actions = {
                    OverlayIconDropdownMenu(
                        entry = DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = "复制登录凭证到剪贴板",
                                    onClick = {
                                        exportType = "clipboard"
                                        showExportWarning = true
                                    }
                                ),
                                DropdownItem(
                                    text = "将登录凭证导出为文件",
                                    onClick = {
                                        exportType = "file"
                                        showExportWarning = true
                                    }
                                )
                            )
                        )
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Share,
                            contentDescription = "导出",
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            )
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
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding())
        ) {
            if (isLoading) {
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
                    contentPadding = PaddingValues(bottom = FeedBottomPads.None),
                    overscrollEffect = null
                ) {
                    // 头像和昵称
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = userProfile?.avatarUrl,
                                contentDescription = "头像",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(40.dp)),
                                contentScale = ContentScale.Crop,
                                error = painterResource(android.R.drawable.ic_menu_gallery)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = userProfile?.nickname ?: "",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            if ((userProfile?.vipType ?: 0) > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "VIP",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // 基本信息
                    item { SmallTitle("基本信息") }
                    item {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            BasicComponent(
                                title = "用户ID",
                                summary = userProfile?.userId?.toString() ?: "空"
                            )
                            BasicComponent(
                                title = "性别",
                                summary = when (userProfile?.gender) {
                                    1 -> "男"
                                    2 -> "女"
                                    else -> "未知"
                                }
                            )
                            BasicComponent(
                                title = "我的村龄",
                                summary = calculateVillageAge(userProfile?.createTime ?: 0)
                            )
                            BasicComponent(
                                title = "IP属地",
                                summary = getProvinceName(userProfile?.province ?: 0)
                            )
                        }
                    }

                    // 个人信息
                    item { SmallTitle("个人信息") }
                    item {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            BasicComponent(
                                title = "生日",
                                summary = formatBirthday(userProfile?.birthday ?: 0)
                            )
                            BasicComponent(
                                title = "地区",
                                summary = "${getProvinceName(userProfile?.province ?: 0)} ${getCityName(userProfile?.city ?: 0)}"
                            )
                            BasicComponent(
                                title = "大学",
                                summary = "空"
                            )
                            BasicComponent(
                                title = "音乐标签",
                                summary = if (userProfile?.expertTags?.isNotEmpty() == true) {
                                    userProfile?.expertTags?.joinToString("、") ?: "空"
                                } else {
                                    "空"
                                }
                            )
                            BasicComponent(
                                title = "简介",
                                summary = userProfile?.signature?.ifEmpty { "空" } ?: "空"
                            )
                        }
                    }

                    // 退出登录按钮
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                showLogoutDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.error,
                                contentColor = MiuixTheme.colorScheme.onError
                            )
                        ) {
                            Text(
                                text = "退出登录",
                                color = MiuixTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }
        }
        
        // 退出登录确认对话框
        OverlayDialog(
            show = showLogoutDialog,
            title = "退出登录",
            summary = "确定要退出登录吗？",
            onDismissRequest = { showLogoutDialog = false },
            content = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showLogoutDialog = false },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = "确定",
                        onClick = {
                            showLogoutDialog = false
                            onLogout()
                            onBackClick()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )

        // 导出警告对话框
        OverlayDialog(
            show = showExportWarning,
            title = "警告",
            summary = "登录凭证为极其重要的数据，其他人也可通过登录凭证登录此账号，请勿随意分享给他人。导出后，登录凭证将会有泄露的风险。",
            onDismissRequest = { showExportWarning = false },
            content = {
                Column {
                    TextButton(
                        text = if (exportButtonEnabled) "已了解风险，继续导出" else "已了解风险，继续导出（${exportCountdown}）",
                        onClick = {
                            if (exportButtonEnabled) {
                                showExportWarning = false
                                if (exportType == "clipboard") {
                                    val cookies = NeteaseApi.getCookie()
                                    val jsonCredentials = formatCookiesToJson(cookies)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("login_credentials", jsonCredentials)
                                    clipboard.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    // 使用SAF文件选择器
                                    fileSaverLauncher.launch("loginkey.json")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = exportButtonEnabled,
                        colors = ButtonDefaults.textButtonColors(
                            color = if (exportButtonEnabled) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.surfaceVariant,
                            textColor = if (exportButtonEnabled) MiuixTheme.colorScheme.onError else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        text = "取消",
                        onClick = { showExportWarning = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}

private fun calculateVillageAge(createTime: Long): String {
    if (createTime <= 0) return "未知"
    val now = System.currentTimeMillis()
    val diff = now - createTime
    val totalDays = diff / (24 * 60 * 60 * 1000)
    val years = totalDays / 365
    val days = totalDays % 365
    return if (years > 0) {
        "${years}年${days}天"
    } else {
        "${days}天"
    }
}

private fun formatBirthday(birthday: Long): String {
    if (birthday <= 0) return "未设置"
    val date = java.util.Date(birthday)
    val format = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.CHINA)
    return format.format(date)
}

private fun getProvinceName(province: Int): String {
    return when (province) {
        110000 -> "北京"
        120000 -> "天津"
        130000 -> "河北"
        140000 -> "山西"
        150000 -> "内蒙古"
        210000 -> "辽宁"
        220000 -> "吉林"
        230000 -> "黑龙江"
        310000 -> "上海"
        320000 -> "江苏"
        330000 -> "浙江"
        340000 -> "安徽"
        350000 -> "福建"
        360000 -> "江西"
        370000 -> "山东"
        410000 -> "河南"
        420000 -> "湖北"
        430000 -> "湖南"
        440000 -> "广东"
        450000 -> "广西"
        460000 -> "海南"
        500000 -> "重庆"
        510000 -> "四川"
        520000 -> "贵州"
        530000 -> "云南"
        540000 -> "西藏"
        610000 -> "陕西"
        620000 -> "甘肃"
        630000 -> "青海"
        640000 -> "宁夏"
        650000 -> "新疆"
        else -> "未知"
    }
}

private fun getCityName(city: Int): String {
    return when (city) {
        230100 -> "哈尔滨"
        230200 -> "齐齐哈尔"
        230300 -> "鸡西"
        230400 -> "鹤岗"
        230500 -> "双鸭山"
        230600 -> "大庆"
        230700 -> "伊春"
        230800 -> "佳木斯"
        230900 -> "七台河"
        231000 -> "牡丹江"
        231100 -> "黑河"
        231200 -> "绥化"
        else -> "未知"
    }
}

private fun formatCookiesToJson(cookies: String): String {
    val cookieMap = LinkedHashMap<String, String>()
    
    // 解析Cookie字符串，去除重复键值
    cookies.split(";").forEach { cookie ->
        val parts = cookie.trim().split("=", limit = 2)
        if (parts.size == 2) {
            val key = parts[0].trim()
            val value = parts[1].trim()
            if (key.isNotEmpty()) {
                cookieMap[key] = value
            }
        }
    }
    
    // 按键名排序
    val sortedMap = cookieMap.toSortedMap()
    
    // 构建JSON字符串
    val json = StringBuilder("{\n")
    val entries = sortedMap.entries.toList()
    entries.forEachIndexed { index, (key, value) ->
        // 转义JSON特殊字符
        val escapedValue = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        json.append("  \"$key\": \"$escapedValue\"")
        if (index < entries.size - 1) {
            json.append(",")
        }
        json.append("\n")
    }
    json.append("}")
    
    return json.toString()
}
