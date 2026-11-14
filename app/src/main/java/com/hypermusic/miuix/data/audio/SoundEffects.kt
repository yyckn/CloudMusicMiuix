package com.hypermusic.miuix.data.audio

import androidx.annotation.Keep
import kotlin.math.roundToInt

/** Sound values use the same physical units as the controls and audio processor. */
@Keep
data class SoundEffects(
    val bands: List<Float> = List(10) { 0f },
    val highpass: Float = 20f,
    val lowpass: Float = 20000f,
    val drive: Float = 0f,
    val crush: Float = 0f,
    val wow: Float = 0f,
    val noise: Float = 0f,
    val width: Float = 1f,
    val space: Float = 0f,
    val punch: Float = 0f,
) {
    fun valid(): Boolean = bands.size == 10 && bands.all { it.isFinite() && it in -12f..12f } &&
        highpass.isFinite() && highpass in 20f..2000f && lowpass.isFinite() && lowpass in 500f..20000f &&
        listOf(drive, crush, wow, noise, space, punch).all { it.isFinite() && it in 0f..1f } &&
        width.isFinite() && width in 0f..2f
}

@Keep
data class UserSoundPreset(val id: String, val name: String, val effects: SoundEffects)

data class BuiltInSoundPreset(val id: String, val name: String, val effects: SoundEffects)

object SoundPresets {
    val frequencies = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val all = listOf(
        BuiltInSoundPreset("flat", "无", SoundEffects()),
        BuiltInSoundPreset("lofi", "Lo-Fi", SoundEffects(listOf(-4f,-2f,0f,2f,3f,2f,0f,-3f,-7f,-10f), 90f,6000f,.28f,.45f,.32f,.22f,.7f,.16f,.4f)),
        BuiltInSoundPreset("radio", "收音机", SoundEffects(listOf(-12f,-8f,-3f,2f,4f,4f,2f,-3f,-8f,-12f), 420f,3800f,.42f,.3f,.1f,.2f,.12f,0f,.6f)),
        BuiltInSoundPreset("hall", "大厅", SoundEffects(listOf(1f,1f,0f,0f,0f,0f,1f,1f,1f,0f), lowpass = 16000f, width = 1.3f, space = .55f, punch = .15f)),
        BuiltInSoundPreset("vocal", "人声", SoundEffects(listOf(-2f,-1f,0f,2f,4f,4f,3f,1f,0f,-1f), highpass = 110f, width = .9f, space = .08f, punch = .35f)),
        BuiltInSoundPreset("bass", "低音增强", SoundEffects(listOf(6f,5f,3f,1f,0f,-1f,-1f,0f,1f,2f), drive = .1f, width = 1.15f, punch = .45f)),
    )
}

enum class LoudnessMode(val label: String) { OFF("关闭"), SMART("智能平衡"), TRACK("单曲"), ALBUM("专辑") }
