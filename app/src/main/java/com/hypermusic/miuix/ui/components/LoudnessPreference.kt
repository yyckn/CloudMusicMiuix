package com.hypermusic.miuix.ui.components

import androidx.compose.runtime.Composable
import com.hypermusic.miuix.data.audio.AudioSettings
import com.hypermusic.miuix.R
import com.hypermusic.miuix.data.audio.LoudnessMode
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
fun LoudnessPreference() {
    WindowDropdownPreference(title = "响度平衡", startAction = { SettingsItemIcon(R.drawable.ic_md3_tune) }, items = LoudnessMode.entries.map { it.label },
        selectedIndex = AudioSettings.loudness.ordinal,
        summary = when (AudioSettings.loudness) {
            LoudnessMode.OFF -> "不调整响度"
            LoudnessMode.SMART -> "根据实际播放的声音测量响度"
            LoudnessMode.TRACK -> "使用歌曲的 ReplayGain 信息"
            LoudnessMode.ALBUM -> "仅在播放专辑时使用专辑 ReplayGain 信息"
        }, onSelectedIndexChange = { AudioSettings.updateLoudness(LoudnessMode.entries[it]) })
}
