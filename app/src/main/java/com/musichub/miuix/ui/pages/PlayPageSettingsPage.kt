package com.musichub.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.musichub.miuix.ui.FeedBottomPads
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import com.musichub.miuix.ui.theme.ThemeSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun PlayPageSettingsPage(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    var lyricFontSize by remember { mutableFloatStateOf(ThemeSettings.lyricFontSize.toFloat()) }
    var translationRatio by remember { mutableFloatStateOf(ThemeSettings.lyricTranslationRatio.toFloat()) }
    var useRoundCover by remember { mutableStateOf(ThemeSettings.useRoundCover) }
    var coverRotationIndex by remember { mutableStateOf(ThemeSettings.coverRotationIndex) }
    var smallScreenCoverStyle by remember { mutableIntStateOf(ThemeSettings.smallScreenCoverStyle) }
    var largeScreenCoverStyle by remember { mutableIntStateOf(ThemeSettings.largeScreenCoverStyle) }
    var coverStyleTab by remember { mutableIntStateOf(0) } // 0=小屏布局, 1=大屏布局
    var enableDynamicCover by remember { mutableStateOf(ThemeSettings.enableDynamicCover) }
    var keepScreenOn by remember { mutableStateOf(ThemeSettings.keepScreenOn) }
    var showCastButton by remember { mutableStateOf(ThemeSettings.showCastButton) }
    var showQuality by remember { mutableStateOf(ThemeSettings.showQuality) }
    var lyricAlignmentIndex by remember { mutableIntStateOf(ThemeSettings.lyricAlignmentIndex) }
    var playerColorSchemeIndex by remember { mutableIntStateOf(ThemeSettings.playerColorSchemeIndex) }

    val coverRotationOptions = remember {
        listOf("静止", "播放时旋转", "始终旋转")
    }

    val lyricAlignmentOptions = remember {
        listOf("靠左", "靠右")
    }

    val playerColorSchemeOptions = remember {
        listOf("跟随深浅色设置", "浅色流光", "深色流光")
    }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "播放页设置",
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
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = FeedBottomPads.None),
            overscrollEffect = null
        ) {
            // 背景颜色
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    WindowDropdownPreference(
                        title = "背景颜色",
                        items = playerColorSchemeOptions,
                        selectedIndex = playerColorSchemeIndex,
                        onSelectedIndexChange = { newIndex ->
                            playerColorSchemeIndex = newIndex
                            ThemeSettings.updatePlayerColorSchemeIndex(newIndex)
                        }
                    )
                }
            }
            // 歌词设置
            item { SmallTitle("歌词样式") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    // 歌词对齐方式
                    WindowDropdownPreference(
                        title = "歌词对齐方式",
                        items = lyricAlignmentOptions,
                        selectedIndex = lyricAlignmentIndex,
                        onSelectedIndexChange = { newIndex ->
                            lyricAlignmentIndex = newIndex
                            ThemeSettings.updateLyricAlignment(newIndex)
                        }
                    )

                    // 歌词文本大小
                    BasicComponent(
                        title = "歌词文本大小",
                        endActions = {
                            Text(
                                text = "${lyricFontSize.toInt()}sp",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        },
                        onClick = {}
                    )
                    Slider(
                        value = lyricFontSize,
                        onValueChange = { newValue ->
                            val rounded = newValue.toInt()
                            lyricFontSize = rounded.toFloat()
                            ThemeSettings.updateLyricFontSize(rounded)
                        },
                        valueRange = 10f..40f,
                        steps = 29,
                        magnetThreshold = 0.1f,
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    // 翻译歌词大小
                    BasicComponent(
                        title = "翻译歌词大小",
                        endActions = {
                            Text(
                                text = "${String.format("%.1f", translationRatio / 100)}x",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        },
                        onClick = {}
                    )
                    Slider(
                        value = translationRatio,
                        onValueChange = { newValue ->
                            // 每10个单位为0.1倍
                            val rounded = (newValue / 10).toInt() * 10
                            translationRatio = rounded.toFloat()
                            ThemeSettings.updateLyricTranslationRatio(rounded)
                        },
                        valueRange = 50f..100f,
                        steps = 5,
                        magnetThreshold = 0.1f,
                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }

            // 封面样式设置
            item { SmallTitle("封面样式") }
            item {
                TabRow(
                    tabs = listOf("小屏布局", "大屏布局"),
                    selectedTabIndex = coverStyleTab,
                    onTabSelected = { coverStyleTab = it },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    val currentCoverStyle = if (coverStyleTab == 0) smallScreenCoverStyle else largeScreenCoverStyle

                    RadioButtonPreference(
                        title = "卡片",
                        selected = currentCoverStyle == 0,
                        onClick = {
                            if (coverStyleTab == 0) {
                                smallScreenCoverStyle = 0
                                ThemeSettings.updateSmallScreenCoverStyle(0)
                            } else {
                                largeScreenCoverStyle = 0
                                ThemeSettings.updateLargeScreenCoverStyle(0)
                            }
                        }
                    )
                    RadioButtonPreference(
                        title = "全屏渐变",
                        selected = currentCoverStyle == 1,
                        onClick = {
                            if (coverStyleTab == 0) {
                                smallScreenCoverStyle = 1
                                ThemeSettings.updateSmallScreenCoverStyle(1)
                            } else {
                                largeScreenCoverStyle = 1
                                ThemeSettings.updateLargeScreenCoverStyle(1)
                            }
                        }
                    )

                    // 仅当当前布局选择「卡片」时才显示圆形封面；选「全屏渐变」则隐藏
                    if (currentCoverStyle == 0) {
                        BasicComponent(
                            title = "使用圆形封面",
                            endActions = {
                                Switch(
                                    checked = useRoundCover,
                                    onCheckedChange = { newValue ->
                                        useRoundCover = newValue
                                        ThemeSettings.updateUseRoundCover(newValue)
                                    }
                                )
                            },
                            onClick = {
                                useRoundCover = !useRoundCover
                                ThemeSettings.updateUseRoundCover(useRoundCover)
                            }
                        )

                        // 封面旋转（仅在圆形封面开启时显示）
                        if (useRoundCover) {
                            WindowDropdownPreference(
                                title = "封面旋转",
                                summary = if (enableDynamicCover && coverRotationIndex > 0) "*动态Live封面不支持封面旋转" else null,
                                items = coverRotationOptions,
                                selectedIndex = coverRotationIndex,
                                onSelectedIndexChange = { newIndex ->
                                    coverRotationIndex = newIndex
                                    ThemeSettings.updateCoverRotationIndex(newIndex)
                                }
                            )
                        }
                    }
                }
            }

            // 动态Live封面（单独包裹）
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    BasicComponent(
                        title = "动态Live封面",
                        summary = "功能维护中，暂不可用",
                        endActions = {
                            Switch(
                                checked = false,
                                enabled = false,
                                onCheckedChange = { }
                            )
                        },
                        onClick = { }
                    )
                }
            }

            // 播放页选项
            item { SmallTitle("其他选项") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    // 显示小米秒播快捷按钮
                    BasicComponent(
                        title = "显示小米妙播快捷按钮",
                        endActions = {
                            Switch(
                                checked = showCastButton,
                                onCheckedChange = { newValue ->
                                    showCastButton = newValue
                                    ThemeSettings.updateShowCastButton(newValue)
                                }
                            )
                        },
                        onClick = {
                            showCastButton = !showCastButton
                            ThemeSettings.updateShowCastButton(showCastButton)
                        }
                    )

                    // 显示当前歌曲音质
                    BasicComponent(
                        title = "显示当前歌曲音质",
                        endActions = {
                            Switch(
                                checked = showQuality,
                                onCheckedChange = { newValue ->
                                    showQuality = newValue
                                    ThemeSettings.updateShowQuality(newValue)
                                }
                            )
                        },
                        onClick = {
                            showQuality = !showQuality
                            ThemeSettings.updateShowQuality(showQuality)
                        }
                    )

                    // 播放页保持屏幕常亮
                    BasicComponent(
                        title = "播放页保持屏幕常亮",
                        endActions = {
                            Switch(
                                checked = keepScreenOn,
                                onCheckedChange = { newValue ->
                                    keepScreenOn = newValue
                                    ThemeSettings.updateKeepScreenOn(newValue)
                                }
                            )
                        },
                        onClick = {
                            keepScreenOn = !keepScreenOn
                            ThemeSettings.updateKeepScreenOn(keepScreenOn)
                        }
                    )
                }
            }
        }
    }
}
