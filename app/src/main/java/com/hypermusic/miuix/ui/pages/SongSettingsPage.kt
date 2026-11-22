package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.PageBackButton
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import com.hypermusic.miuix.ui.navigation.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.R
import com.hypermusic.miuix.ui.components.SettingsItemIcon
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.theme.ThemeSettings
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.model.AudioQuality
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import androidx.compose.material.icons.Icons
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SongSettingsPage(
    viewModel: MusicPlayerViewModel,
    onBackClick: () -> Unit,
    onSoundEffectsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberPageBlurBackdrop()
    var showSpeedDialog by remember { mutableStateOf(false) }
    var speedInput by remember { mutableStateOf("") }
    var speedDraft by remember(viewModel.playbackSpeed) { mutableStateOf(viewModel.playbackSpeed) }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "音频设置",
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
            contentPadding = PaddingValues(top = pageScrollTopInset(paddingValues.calculateTopPadding()), bottom = FeedBottomPads.None),
            overscrollEffect = null
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SmallTitle("音质") }
            item {
                Text(AudioQuality.summary, modifier = Modifier.padding(start = 26.dp, end = 26.dp, bottom = 8.dp),
                    style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    for (platform in MusicPlatform.entries) {
                        WindowDropdownPreference(
                            title = when (platform) {
                                MusicPlatform.NETEASE -> "网易云音乐"
                                MusicPlatform.QQ -> "QQ音乐"
                                MusicPlatform.BODIAN -> "波点音乐"
                                MusicPlatform.KUGOU -> "酷狗音乐"
                                MusicPlatform.QISHUI -> "汽水音乐"
                            },
                            items = AudioQuality.options(platform),
                            selectedIndex = viewModel.platformAudioQualityIndex(platform),
                            onSelectedIndexChange = { viewModel.updatePlatformAudioQualityIndex(platform, it) }
                        )
                    }
                }
            }
            item { SmallTitle("播放选项") }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    com.hypermusic.miuix.ui.components.LoudnessPreference()
                    ArrowPreference(title = "音效", startAction = { SettingsItemIcon(R.drawable.ic_md3_equalizer) }, onClick = onSoundEffectsClick)

                    ArrowPreference(
                        title = "播放速度",
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_speed) },
                        endActions = {
                            Text(
                                text = "${formatSongSpeedValue(speedDraft)}x",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        },
                        bottomAction = {
                            Slider(
                                value = speedDraft,
                                onValueChange = { newValue ->
                                    speedDraft = newValue
                                },
                                onValueChangeFinished = { viewModel.updatePlaybackSpeed(speedDraft) },
                                valueRange = 0.5f..3f,
                                steps = 24,
                                showKeyPoints = true,
                                keyPoints = listOf(1f),
                                magnetThreshold = 0.005f,
                                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        },
                        onClick = {
                            speedInput = formatSongSpeedValue(viewModel.playbackSpeed)
                            showSpeedDialog = true
                        }
                    )

                    SwitchPreference(
                        title = "渐入渐出",
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_line_curve) },
                        summary = "在播放和暂停歌曲时音频渐入渐出",
                        checked = ThemeSettings.fadeEnabled,
                        onCheckedChange = { ThemeSettings.updateFadeEnabled(it) }
                    )
                }
            }
        }

        OverlayDialog(
            show = showSpeedDialog,
            title = "调节播放速度",
            summary = "输入0.5-3之间的数字，精度为0.1",
            onDismissRequest = { showSpeedDialog = false },
            content = {
                Column {
                    TextField(
                        value = speedInput,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                speedInput = newValue
                            }
                        },
                        label = "0.5 - 3",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showSpeedDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = "确定",
                            onClick = {
                                speedInput.toFloatOrNull()?.let { viewModel.updatePlaybackSpeed(it) }
                                showSpeedDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        )
    }
}

private fun formatSongSpeedValue(speed: Float): String {
    val hundredths = (speed * 100f).roundToInt()
    return if (hundredths % 10 == 0) {
        String.format(java.util.Locale.US, "%.1f", speed)
    } else {
        String.format(java.util.Locale.US, "%.2f", speed)
    }
}
