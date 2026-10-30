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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.musichub.miuix.ui.components.BlurredBar
import com.musichub.miuix.ui.components.pageLayerBackdrop
import com.musichub.miuix.ui.components.rememberPageBlurBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.musichub.miuix.ui.FeedBottomPads
import com.musichub.miuix.ui.theme.ThemeSettings
import com.musichub.miuix.data.lyrics.LyricUpgradeBlacklist
import com.musichub.miuix.data.lyrics.LyricUpgradeRequiredList
import com.musichub.miuix.data.lyrics.LyricExclusionRules
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack

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
    var qqEnabled by remember { mutableStateOf(ThemeSettings.qqLyricsEnabled) }
    var kugouEnabled by remember { mutableStateOf(ThemeSettings.kugouLyricsEnabled) }
    var showJapaneseRomaji by remember { mutableStateOf(ThemeSettings.showJapaneseRomaji) }
    var japaneseNoUpgrade by remember { mutableStateOf(ThemeSettings.japaneseNoUpgrade) }
    var exclusionEnabled by remember { mutableStateOf(LyricExclusionRules.enabled) }

    // 网易云音乐始终启用；QQ/酷狗关闭后不在偏好选项中出现
    val sourceOptions = buildList {
        add(0 to "智能选择")
        add(1 to "优先网易云音乐")
        if (qqEnabled) add(2 to "优先QQ音乐")
        if (kugouEnabled) add(3 to "优先酷狗音乐")
    }

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
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pageLayerBackdrop(blurBackdrop),
            contentPadding = PaddingValues(bottom = FeedBottomPads.None)
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
                        title = "QQ音乐",
                        checked = qqEnabled,
                        onCheckedChange = { enabled ->
                            qqEnabled = enabled
                            ThemeSettings.updateQqLyricsEnabled(enabled)
                            if (!enabled && sourceIndex == 2) {
                                sourceIndex = 0
                                ThemeSettings.updateLyricSourcePreferenceIndex(0)
                            }
                            onLyricSettingsChanged()
                        }
                    )
                    SwitchPreference(
                        title = "酷狗音乐",
                        checked = kugouEnabled,
                        onCheckedChange = { enabled ->
                            kugouEnabled = enabled
                            ThemeSettings.updateKugouLyricsEnabled(enabled)
                            if (!enabled && sourceIndex == 3) {
                                sourceIndex = 0
                                ThemeSettings.updateLyricSourcePreferenceIndex(0)
                            }
                            onLyricSettingsChanged()
                        }
                    )
                    SwitchPreference(
                        title = "日语歌曲显示罗马音",
                        summary = "此功能由网易云提供，仅限在使用网易云平台的歌词时使用",
                        checked = showJapaneseRomaji,
                        onCheckedChange = { enabled ->
                            showJapaneseRomaji = enabled
                            ThemeSettings.updateShowJapaneseRomaji(enabled)
                            onLyricSettingsChanged()
                        }
                    )
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
                            summary = "当前歌词非逐字歌词时，尝试从其他平台获取逐字歌词覆盖",
                            checked = autoUpgrade,
                            onCheckedChange = { enabled ->
                                autoUpgrade = enabled
                                ThemeSettings.updateAutoUpgradeLyrics(enabled)
                                if (enabled) onLyricSettingsChanged()
                            }
                        )
                        if (autoUpgrade && showJapaneseRomaji) {
                            SwitchPreference(
                                title = "日语歌曲不升级为其他平台的逐字歌词",
                                summary = "因除网易云音乐的其他平台暂无法获取罗马音，故提供此选项便于显示歌词罗马音",
                                checked = japaneseNoUpgrade,
                                onCheckedChange = { enabled ->
                                    japaneseNoUpgrade = enabled
                                    ThemeSettings.updateJapaneseNoUpgrade(enabled)
                                    onLyricSettingsChanged()
                                }
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = "管理歌曲是否需要升级逐字歌词",
                        summary = "需要升级 ${LyricUpgradeRequiredList.entries.size} 首 · " +
                            "不需要升级 ${LyricUpgradeBlacklist.entries.size} 首",
                        onClick = onManageLyricUpgradesClick
                    )
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
