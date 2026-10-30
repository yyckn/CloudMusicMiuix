package com.musichub.miuix.data.model

/**
 * 内置音效预设。
 *
 * [bandLevels] 为 5 段均衡曲线的毫贝值（60Hz / 230Hz / 910Hz / 3.6kHz / 14kHz），
 * 实际设备会按可用频段数量线性映射。
 */
data class AudioEffectPreset(
    val id: String,
    val name: String,
    val description: String,
    val bandLevels: List<Short> = emptyList(),
    val bassBoost: Short = 0,
    val loudnessGain: Int = 0,
    val reverb: AudioReverb = AudioReverb.NONE,
    val virtualizerStrength: Short = 0
)

enum class AudioReverb {
    NONE,
    LARGE_HALL
}

object AudioEffectPresets {
    const val DEFAULT_ID = "default"

    val default = AudioEffectPreset(
        id = DEFAULT_ID,
        name = "默认音效",
        description = "不添加任何音效"
    )

    val presets: List<AudioEffectPreset> = listOf(
        default,
        AudioEffectPreset(
            id = "pop",
            name = "流行",
            description = "提升中高频，人声更清晰",
            bandLevels = listOf<Short>(200, 100, 0, 200, 300)
        ),
        AudioEffectPreset(
            id = "rock",
            name = "摇滚",
            description = "增强低频与高频，更有冲击力",
            bandLevels = listOf<Short>(500, 200, -100, 300, 500),
            bassBoost = 300
        ),
        AudioEffectPreset(
            id = "jazz",
            name = "爵士",
            description = "温暖的中频与柔和的高频",
            bandLevels = listOf<Short>(300, 200, 100, 200, 200)
        ),
        AudioEffectPreset(
            id = "classical",
            name = "古典",
            description = "均衡、通透的听感",
            bandLevels = listOf<Short>(400, 200, 0, 200, 400)
        ),
        AudioEffectPreset(
            id = "hall",
            name = "音乐厅",
            description = "扩展声场，营造音乐厅的空间感",
            bandLevels = listOf<Short>(250, 120, -80, 120, 250),
            reverb = AudioReverb.LARGE_HALL,
            virtualizerStrength = 1000
        ),
        AudioEffectPreset(
            id = "bass",
            name = "低音增强",
            description = "强化低频下潜与力度",
            bandLevels = listOf<Short>(700, 400, 0, 0, 0),
            bassBoost = 600
        ),
        AudioEffectPreset(
            id = "vocal",
            name = "人声",
            description = "突出中频人声",
            bandLevels = listOf<Short>(-200, -100, 400, 500, 200)
        ),
        AudioEffectPreset(
            id = "night",
            name = "夜间",
            description = "降低高低频，适合低音量聆听",
            bandLevels = listOf<Short>(-400, -200, 0, -100, -300)
        )
    )

    fun byId(id: String): AudioEffectPreset =
        presets.firstOrNull { it.id == id } ?: default
}
