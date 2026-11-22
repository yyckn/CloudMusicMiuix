package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.PageBackButton
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import com.hypermusic.miuix.ui.navigation.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.R
import com.hypermusic.miuix.ui.components.SettingsItemIcon
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.theme.ThemeSettings
import com.hypermusic.miuix.data.lyrics.LyricExclusionRules
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.material.icons.Icons

@Composable
fun LyricSettingsPage(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onWordByWordLyricsChanged: () -> Unit = {},
    onLyricSettingsChanged: () -> Unit = {},
    onManageLyricUpgradesClick: () -> Unit = {},
    onExclusionRulesClick: () -> Unit = {}
) {
    val scrollBehavior = MiuixScrollBehavior()
    var wordByWord by remember { mutableStateOf(ThemeSettings.wordByWordLyrics) }
    var sourceIndex by remember { mutableStateOf(ThemeSettings.lyricSourcePreferenceIndex) }
    var autoUpgrade by remember { mutableStateOf(ThemeSettings.autoUpgradeLyrics) }
    var showJapaneseRomaji by remember { mutableStateOf(ThemeSettings.showJapaneseRomaji) }
    var exclusionEnabled by remember { mutableStateOf(LyricExclusionRules.enabled) }

    val sourceOptions = listOf(
        0 to "智能选择", 1 to "优先网易云音乐", 2 to "优先QQ音乐", 3 to "优先酷狗音乐", 4 to "优先Apple Music"
    )

    BackHandler { onBackClick() }

    val blurBackdrop = rememberPageBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "歌词设置",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = { PageBackButton(onClick = { onBackClick() }) }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = pageViewportTopInset(paddingValues.calculateTopPadding()))
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pageLayerBackdrop(blurBackdrop),
            contentPadding = PaddingValues(top = pageScrollTopInset(paddingValues.calculateTopPadding()), bottom = FeedBottomPads.None)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item { SmallTitle("歌词来源") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    WindowDropdownPreference(
                        title = "歌词来源偏好",
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_category) },
                        items = sourceOptions.map { it.second },
                        selectedIndex = sourceOptions
                            .indexOfFirst { it.first == sourceIndex }
                            .coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            val value = sourceOptions.getOrNull(index)?.first ?: 0
                            sourceIndex = value
                            ThemeSettings.updateLyricSourcePreferenceIndex(value)
                            onLyricSettingsChanged()
                        }
                    )
                    SwitchPreference(
                        title = "粤日韩语言的歌曲显示罗马音",
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_letter_switch) },
                        summary = "此功能由网易云音乐和QQ音乐提供，仅供参考",
                        checked = showJapaneseRomaji,
                        onCheckedChange = { enabled ->
                            showJapaneseRomaji = enabled
                            ThemeSettings.updateShowJapaneseRomaji(enabled)
                            onLyricSettingsChanged()
                        }
                    )
                    ArrowPreference(title = "管理歌词匹配", startAction = { SettingsItemIcon(R.drawable.ic_md3_swap_horiz) }, onClick = onManageLyricUpgradesClick)
                }
            }

            item { SmallTitle("逐字歌词") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    SwitchPreference(
                        title = "逐字歌词",
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_match_case) },
                        summary = "启用卡拉OK逐字歌词",
                        checked = wordByWord,
                        onCheckedChange = { enabled ->
                            wordByWord = enabled
                            ThemeSettings.updateWordByWordLyrics(enabled)
                            onWordByWordLyricsChanged()
                            if (enabled && autoUpgrade) onLyricSettingsChanged()
                        }
                    )
                    if (wordByWord) {
                        SwitchPreference(
                            title = "自动升级为逐字歌词",
                            startAction = { SettingsItemIcon(R.drawable.ic_md3_keyboard_double_arrow_up) },
                            summary = "当前歌词非逐字歌词时，尝试从其他平台获取逐字歌词覆盖",
                            checked = autoUpgrade,
                            onCheckedChange = { enabled ->
                                autoUpgrade = enabled
                                ThemeSettings.updateAutoUpgradeLyrics(enabled)
                                if (enabled) onLyricSettingsChanged()
                            }
                        )

                    }
                }
            }

            item { SmallTitle("歌词处理") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    SwitchPreference(
                        title = "启用歌词排除",
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_contract_delete) },
                        summary = "自动清理放在歌词头尾的元数据行（如词、曲、编曲、制作人等）",
                        checked = exclusionEnabled,
                        onCheckedChange = { enabled ->
                            exclusionEnabled = enabled
                            LyricExclusionRules.updateEnabled(enabled)
                            onLyricSettingsChanged()
                        }
                    )
                    if (exclusionEnabled) {
                        ArrowPreference(
                            title = "歌词排除规则",
                            startAction = { SettingsItemIcon(R.drawable.ic_md3_rule) },
                            summary = "默认规则已启用 ${LyricExclusionRules.enabledDefaultRuleCount}/" +
                                "${LyricExclusionRules.defaultRules.size} 条 · " +
                                "用户规则 ${LyricExclusionRules.userRules.size} 条",
                            onClick = onExclusionRulesClick
                        )
                    }
                }
            }
        }
    }
}
