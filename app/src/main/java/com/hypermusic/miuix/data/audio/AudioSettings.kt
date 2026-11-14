package com.hypermusic.miuix.data.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/** A working copy is persisted separately from named presets so editing never overwrites one. */
object AudioSettings {
    private val gson = Gson()
    private var prefs: android.content.SharedPreferences? = null
    @Volatile private var initialized = false
    var current by mutableStateOf(SoundEffects())
        private set
    var selectedId by mutableStateOf("flat")
        private set
    var userPresets by mutableStateOf<List<UserSoundPreset>>(emptyList())
        private set
    var builtInOverrides by mutableStateOf<Map<String, SoundEffects>>(emptyMap())
        private set
    var loudness by mutableStateOf(LoudnessMode.OFF)
        private set
    @Volatile var playbackEffects: SoundEffects = SoundEffects()
        private set
    @Volatile var playbackLoudness: LoudnessMode = LoudnessMode.OFF
        private set

    val selectedEffects: SoundEffects?
        get() = builtInEffects(selectedId) ?: userPresets.firstOrNull { it.id == selectedId }?.effects
    val modified: Boolean get() = selectedEffects != current

    fun builtInEffects(id: String): SoundEffects? =
        SoundPresets.all.firstOrNull { it.id == id }?.let { builtInOverrides[id] ?: it.effects }

    @Synchronized fun init(context: Context) {
        if (initialized) return
        val store = context.applicationContext.getSharedPreferences("audio_effects", Context.MODE_PRIVATE)
        prefs = store
        current = runCatching { gson.fromJson(store.getString("current", null), SoundEffects::class.java)?.takeIf { it.valid() } }.getOrNull() ?: SoundEffects()
        playbackEffects = current
        userPresets = runCatching {
            gson.fromJson<List<UserSoundPreset>>(store.getString("presets", "[]"), object : TypeToken<List<UserSoundPreset>>() {}.type)
        }.getOrNull().orEmpty().filter { preset ->
            runCatching { preset.id.isNotBlank() && preset.name.isNotBlank() && preset.effects.valid() }.getOrDefault(false)
        }
        builtInOverrides = runCatching {
            gson.fromJson<Map<String, SoundEffects>>(store.getString("built_in_overrides", "{}"), object : TypeToken<Map<String, SoundEffects>>() {}.type)
        }.getOrNull().orEmpty().filter { (id, value) ->
            SoundPresets.all.any { it.id == id } && runCatching { value.valid() }.getOrDefault(false)
        }
        selectedId = store.getString("selected_id", "flat").orEmpty().ifBlank { "flat" }
        loudness = LoudnessMode.entries.getOrNull(store.getInt("loudness", 0)) ?: LoudnessMode.OFF
        playbackLoudness = loudness
        initialized = true
    }
    fun select(id: String) {
        val value = builtInEffects(id) ?: userPresets.firstOrNull { it.id == id }?.effects ?: return
        selectedId = id
        set(value)
    }
    fun set(value: SoundEffects) {
        require(value.valid())
        current = value.copy(bands = value.bands.toList())
        playbackEffects = current
        prefs?.edit()?.putString("current", gson.toJson(current))?.putString("selected_id", selectedId)?.apply()
    }
    fun updateLoudness(value: LoudnessMode) {
        loudness = value; playbackLoudness = value
        prefs?.edit()?.putInt("loudness", value.ordinal)?.apply()
    }
    fun saveNew(name: String, effects: SoundEffects = current): UserSoundPreset {
        val cleanName = name.trim().take(80)
        require(cleanName.isNotEmpty() && cleanName.none(Char::isISOControl) && effects.valid())
        val preset = UserSoundPreset(UUID.randomUUID().toString(), cleanName, effects.copy(bands = effects.bands.toList()))
        userPresets = userPresets + preset; selectedId = preset.id; current = preset.effects; playbackEffects = current
        persist()
        return preset
    }
    fun overwriteSelected(): Boolean {
        if (SoundPresets.all.any { it.id == selectedId }) {
            builtInOverrides = builtInOverrides + (selectedId to current.copy(bands = current.bands.toList()))
        } else {
            if (userPresets.none { it.id == selectedId }) return false
            userPresets = userPresets.map { if (it.id == selectedId) it.copy(effects = current.copy(bands = current.bands.toList())) else it }
        }
        persist(); return true
    }
    fun remove(id: String) {
        userPresets = userPresets.filterNot { it.id == id }
        if (selectedId == id) selectedId = ""
        persist()
    }
    private fun persist() {
        prefs?.edit()?.putString("presets", gson.toJson(userPresets))?.putString("built_in_overrides", gson.toJson(builtInOverrides))?.putString("current", gson.toJson(current))
            ?.putString("selected_id", selectedId)?.apply()
    }
}
