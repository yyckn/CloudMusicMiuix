package com.musichub.miuix.ui.pages

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.musichub.miuix.ui.FeedBottomPads
import com.musichub.miuix.ui.theme.ThemeSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun ThemeSettingsPage(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    
    val themeModeOptions = remember {
        listOf("浅色模式", "深色模式", "跟随系统")
    }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "界面设置",
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
            item { Spacer(modifier = Modifier.size(12.dp)) }
            item { SmallTitle("主题色") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    WindowDropdownPreference(
                        title = "主题模式",
                        items = themeModeOptions,
                        selectedIndex = ThemeSettings.themeModeIndex,
                        onSelectedIndexChange = { newIndex ->
                            ThemeSettings.updateThemeModeIndex(newIndex)
                        }
                    )
                    BasicComponent(
                        title = "跟随系统主题色（莫奈取色）",
                        summary = "从系统壁纸中提取颜色作为主题色，仅限Android12及以上使用",
                        endActions = {
                            Switch(
                                checked = ThemeSettings.followMonet,
                                onCheckedChange = { ThemeSettings.updateFollowMonet(it) }
                            )
                        },
                        onClick = { ThemeSettings.updateFollowMonet(!ThemeSettings.followMonet) }
                    )
                }
            }
            item {
                Text(
                    text = "无法使用莫奈取色？如果您的系统为安卓定制系统（如HyperOS、OriginOS、ColorOS、MagicOS等），且系统底层在Android12及以上，可使用下方开源项目强制更改系统主题色：",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = "ColorBlendr",
                        summary = "点击此处跳转到项目仓库",
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Mahmud0808/ColorBlendr"))
                                )
                            } catch (_: Exception) {
                            }
                        }
                    )
                }
            }
            item { SmallTitle("底栏") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    BasicComponent(
                        title = "启用悬浮底栏",
                        summary = if (ThemeSettings.useFloatingBar) "已启用" else "未启用",
                        endActions = {
                            Switch(
                                checked = ThemeSettings.useFloatingBar,
                                onCheckedChange = { ThemeSettings.updateUseFloatingBar(it) }
                            )
                        },
                        onClick = { ThemeSettings.updateUseFloatingBar(!ThemeSettings.useFloatingBar) }
                    )
                }
            }
        }
    }
}
