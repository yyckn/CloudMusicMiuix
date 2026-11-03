package com.hypermusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
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
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.theme.ThemeSettings
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun SongSettingsPage(
    viewModel: MusicPlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberPageBlurBackdrop()
    val qualityOptions = com.hypermusic.miuix.data.model.AudioQuality.options(com.hypermusic.miuix.data.api.PlatformManager.current)
    var showSpeedDialog by remember { mutableStateOf(false) }
    var speedInput by remember { mutableStateOf("") }

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "歌曲设置",
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
            item { SmallTitle("播放选项") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    WindowDropdownPreference(
                        title = "音质",
                        summary = "设置播放及下载的最高音质；播放时切歌生效，实际音质取决于歌曲和账号权限",
                        items = qualityOptions,
                        selectedIndex = viewModel.audioQualityIndex,
                        startAction = { Icon(imageVector = MiuixIcons.Music, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        onSelectedIndexChange = { viewModel.updateAudioQualityIndex(it) }
                    )

                    BasicComponent(
                        title = "播放速度",
                        endActions = {
                            Text(
                                text = "${formatSongSpeedValue(viewModel.playbackSpeed)}x",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        },
                        bottomAction = {
                            Slider(
                                value = viewModel.playbackSpeed,
                                onValueChange = { newValue ->
                                    viewModel.updatePlaybackSpeed(newValue)
                                },
                                valueRange = 0.1f..5f,
                                steps = 48,
                                magnetThreshold = 0.1f,
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
            summary = "输入0.1-5之间的数字",
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
                        label = "0.1 - 5",
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
