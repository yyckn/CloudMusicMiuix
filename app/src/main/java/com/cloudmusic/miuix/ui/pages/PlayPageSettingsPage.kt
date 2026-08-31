package com.cloudmusic.miuix.ui.pages

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import com.cloudmusic.miuix.ui.theme.ThemeSettings
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
    var coverStyleIndex by remember { mutableStateOf(ThemeSettings.coverStyleIndex) } // 0=卡片, 1=全屏渐变
    var keepScreenOn by remember { mutableStateOf(ThemeSettings.keepScreenOn) }
    var lyricCenter by remember { mutableStateOf(ThemeSettings.lyricCenter) }

    val coverRotationOptions = remember {
        listOf("静止", "播放时旋转", "始终旋转")
    }

    val coverStyleOptions = remember {
        listOf("卡片", "全屏渐变", "竖屏使用卡片，横屏使用全屏渐变")
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
            contentPadding = PaddingValues(bottom = 16.dp),
            overscrollEffect = null
        ) {
            // 歌词设置
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SmallTitle("歌词") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    // 保持歌词居中
                    BasicComponent(
                        title = "保持歌词居中",
                        endActions = {
                            Switch(
                                checked = lyricCenter,
                                onCheckedChange = { newValue ->
                                    lyricCenter = newValue
                                    ThemeSettings.updateLyricCenter(newValue)
                                }
                            )
                        },
                        onClick = {
                            lyricCenter = !lyricCenter
                            ThemeSettings.updateLyricCenter(lyricCenter)
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
                            val rounded = (newValue / 2).toInt() * 2
                            lyricFontSize = rounded.toFloat()
                            ThemeSettings.updateLyricFontSize(rounded)
                        },
                        valueRange = 10f..40f,
                        steps = 14,
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

            // 封面设置
            item { SmallTitle("封面选项") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    // 封面样式
                    WindowDropdownPreference(
                        title = "封面样式",
                        items = coverStyleOptions,
                        selectedIndex = coverStyleIndex,
                        onSelectedIndexChange = { newIndex ->
                            coverStyleIndex = newIndex
                            ThemeSettings.updateCoverStyleIndex(newIndex)
                        }
                    )

                    // 使用圆形封面（仅在封面样式为卡片或自动切换时显示）
                    if (coverStyleIndex == 0 || coverStyleIndex == 2) {
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

            // 播放页选项
            item { SmallTitle("播放页选项") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
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
