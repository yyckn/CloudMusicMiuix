package com.hypermusic.miuix.ui.pages

import com.hypermusic.miuix.ui.navigation.BackHandler
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.R
import com.hypermusic.miuix.ui.components.SettingsItemIcon
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.theme.KeyboardShortcutSettings
import com.hypermusic.miuix.ui.theme.ShortcutAction
import com.hypermusic.miuix.ui.theme.ThemeSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.utils.overScrollVertical

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
    // Use the same window-width breakpoint as the player itself.
    val isLargeScreen = LocalConfiguration.current.screenWidthDp > 600
    var coverStyleTab by remember(isLargeScreen) {
        mutableIntStateOf(if (isLargeScreen) 1 else 0)
    } // 0=小屏布局, 1=大屏布局
    var keepScreenOn by remember { mutableStateOf(ThemeSettings.keepScreenOn) }
    var showCastButton by remember { mutableStateOf(ThemeSettings.showCastButton) }
    var showQuality by remember { mutableStateOf(ThemeSettings.showQuality) }
    var playerColorSchemeIndex by remember { mutableIntStateOf(ThemeSettings.playerColorSchemeIndex) }

    val coverRotationOptions = remember {
        listOf("静止", "播放时旋转", "始终旋转")
    }

    val playerColorSchemeOptions = remember {
        listOf("跟随深浅色设置", "浅色流光", "深色流光（推荐）")
    }

    BackHandler {
        onBackClick()
    }

    val blurBackdrop = rememberPageBlurBackdrop()
    var shortcutsEnabled by remember { mutableStateOf(KeyboardShortcutSettings.enabled) }
    var rebindingAction by remember { mutableStateOf<ShortcutAction?>(null) }
    val shortcutFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    LaunchedEffect(rebindingAction) {
        if (rebindingAction != null) {
            shortcutFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(shortcutFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val action = rebindingAction ?: return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                if (KeyboardShortcutSettings.isModifierKey(keyCode)) return@onPreviewKeyEvent true
                val modifiers = event.nativeKeyEvent.metaState
                val conflict = ShortcutAction.entries.firstOrNull { other ->
                    other != action && KeyboardShortcutSettings.matches(other, keyCode, modifiers)
                }
                if (conflict != null) {
                    Toast.makeText(
                        context,
                        "快捷键与「${conflict.title}」冲突，未保存",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    KeyboardShortcutSettings.updateKey(action, keyCode, modifiers)
                }
                rebindingAction = null
                focusManager.clearFocus()
                true
            }
    ) {
        Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "播放页设置",
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
            contentPadding = PaddingValues(bottom = FeedBottomPads.None),
            overscrollEffect = null
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }
            // 布局样式设置
            item { SmallTitle("封面样式") }
            item {
                val currentCoverStyle = if (coverStyleTab == 0) smallScreenCoverStyle else largeScreenCoverStyle
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    TabRowWithContour(
                        tabs = listOf("小屏布局", "大屏布局"),
                        selectedTabIndex = coverStyleTab,
                        onTabSelected = { coverStyleTab = it },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                    RadioButtonPreference(
                        title = "卡片封面",
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
                    if (coverStyleTab == 1) {
                        RadioButtonPreference(
                            title = "卡片封面（车机布局）",
                            selected = currentCoverStyle == 2,
                            onClick = {
                                largeScreenCoverStyle = 2
                                ThemeSettings.updateLargeScreenCoverStyle(2)
                            }
                        )
                    }
                    RadioButtonPreference(
                        title = "黑胶唱片",
                        selected = currentCoverStyle == 3,
                        onClick = {
                            if (coverStyleTab == 0) {
                                smallScreenCoverStyle = 3
                                ThemeSettings.updateSmallScreenCoverStyle(3)
                            } else {
                                largeScreenCoverStyle = 3
                                ThemeSettings.updateLargeScreenCoverStyle(3)
                            }
                        }
                    )
                    RadioButtonPreference(
                        title = "渐变封面",
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
                }

                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    if (currentCoverStyle == 3 && coverStyleTab == 0) {
                        WindowDropdownPreference(
                            title = "左右滑动黑胶唱片的操作",
                            items = listOf("切换歌曲", "切换封面和歌词"),
                            selectedIndex = ThemeSettings.vinylSwipeAction,
                            onSelectedIndexChange = { ThemeSettings.updateVinylSwipeAction(it) }
                        )
                    }
                    // 卡片和车机布局共用圆形封面与旋转设置。
                    if (currentCoverStyle == 0 || (coverStyleTab == 1 && currentCoverStyle == 2)) {
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

                    WindowDropdownPreference(
                        title = "背景颜色",
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_texture) },
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
                        bottomAction = {
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
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
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
                        bottomAction = {
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
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    )
                }
            }

            // 快捷键
            item { SmallTitle("快捷键") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    BasicComponent(
                        title = "键盘快捷键",
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_keyboard) },
                        endActions = {
                            Switch(
                                checked = shortcutsEnabled,
                                onCheckedChange = { newValue ->
                                    shortcutsEnabled = newValue
                                    KeyboardShortcutSettings.updateEnabled(newValue)
                                    if (!newValue) rebindingAction = null
                                }
                            )
                        },
                        onClick = {
                            shortcutsEnabled = !shortcutsEnabled
                            KeyboardShortcutSettings.updateEnabled(shortcutsEnabled)
                            if (!shortcutsEnabled) rebindingAction = null
                        }
                    )

                    if (shortcutsEnabled) {
                        ShortcutAction.entries.forEach { action ->
                            BasicComponent(
                                title = action.title,
                                endActions = {
                                    Text(
                                        text = if (rebindingAction == action) {
                                            "按下按键或 Ctrl / Shift / Alt 组合键"
                                        } else {
                                            KeyboardShortcutSettings.keyLabel(action)
                                        },
                                        style = MiuixTheme.textStyles.body2,
                                        color = if (rebindingAction == action) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        }
                                    )
                                },
                                onClick = { rebindingAction = action }
                            )
                        }
                    }
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
                    // 显示小米妙播快捷按钮
                    BasicComponent(
                        title = "显示音频投放按钮",
                        startAction = { Icon(MiuixIcons.ScreenMirroring, contentDescription = null, modifier = Modifier.size(24.dp)) },
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
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_audio_description) },
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
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_display_settings) },
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
}
