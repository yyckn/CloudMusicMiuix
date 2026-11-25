package com.hypermusic.miuix.data.audio.separation

import android.content.Context
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** 人声分离模型元数据。全部为 UVR MDX-Net 系列，适配端侧 ONNX Runtime。 */
enum class VocalSeparationModel(
    val id: String,
    val label: String,
    val summary: String,
    val nFft: Int,
    val hopLength: Int,
    val dimF: Int,
    val dimT: Int,
    val compensate: Float,
    val vocalsIsPrimary: Boolean,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    KUIELAB_B_VOCALS(
        id = "kuielab_b_vocals",
        label = "KUILab B Vocals (快速)",
        summary = "约28MB，分离最快，适合日常实时切换人声或伴奏",
        nFft = 6144,
        hopLength = 1024,
        dimF = 2048,
        dimT = 256,
        compensate = 1.035f,
        vocalsIsPrimary = true,
        fileName = "kuielab_b_vocals.onnx",
        url = "https://coldp.github.io/hyperchanger-updateinfo/models/kuielab_b_vocals.onnx",
        sizeBytes = 29_703_204L,
        sha256 = "9b7dcb9d878acb0f3e64ff3fd27750faae96577013f6d50f5996875bf4250713",
    ),
    KIM_VOCAL_2(
        id = "kim_vocal_2",
        label = "Kim Vocal 2 (均衡)",
        summary = "约64MB，人声还原更加干净细腻，但慢于上一模型",
        nFft = 7680,
        hopLength = 1024,
        dimF = 3072,
        dimT = 256,
        compensate = 1.009f,
        vocalsIsPrimary = true,
        fileName = "Kim_Vocal_2.onnx",
        url = "https://coldp.github.io/hyperchanger-updateinfo/models/Kim_Vocal_2.onnx",
        sizeBytes = 66_759_214L,
        sha256 = "ce74ef3b6a6024ce44211a07be9cf8bc6d87728cc852a68ab34eb8e58cde9c8b",
    ),
    UVR_MDX_INST_HQ_4(
        id = "uvr_mdx_inst_hq_4",
        label = "UVR Inst HQ 4 (质量)",
        summary = "约56MB，优先保证人声和伴奏纯净度，但分离速度较慢",
        nFft = 5120,
        hopLength = 1024,
        dimF = 2560,
        dimT = 256,
        compensate = 1.019f,
        vocalsIsPrimary = false,
        fileName = "UVR-MDX-NET-Inst_HQ_4.onnx",
        url = "https://coldp.github.io/hyperchanger-updateinfo/models/UVR-MDX-NET-Inst_HQ_4.onnx",
        sizeBytes = 59_074_342L,
        sha256 = "3c4b5b9b05090fdf238f38ba5046813982d50e2a652e9cb3324ea79720c3c9c8",
    );

    companion object {
        val DEFAULT: VocalSeparationModel = KUIELAB_B_VOCALS
        fun byId(id: String?): VocalSeparationModel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

data class VocalSeparationMix(val position: Int) {
    init {
        require(position in 0..MAX_POSITION)
    }

    val instrumentalGain: Float get() = (MAX_POSITION - position).coerceAtMost(10) / 10f
    val vocalGain: Float get() = position.coerceAtMost(10) / 10f

    companion object {
        const val MAX_POSITION = 20
        val ORIGINAL = VocalSeparationMix(10)
    }
}

data class VocalSeparationConfig(
    val enabled: Boolean = false,
    val model: VocalSeparationModel = VocalSeparationModel.DEFAULT,
    val hardwareAcceleration: Boolean = true,
    val mix: VocalSeparationMix = VocalSeparationMix.ORIGINAL,
)

object VocalSeparationConfigCodec {
    fun encode(config: VocalSeparationConfig): String = JSONObject().apply {
        put("enabled", config.enabled)
        put("model", config.model.id)
        put("hardwareAcceleration", config.hardwareAcceleration)
        put("mixPosition", config.mix.position)
    }.toString()

    fun decode(raw: String?): VocalSeparationConfig {
        if (raw.isNullOrBlank()) return VocalSeparationConfig()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return VocalSeparationConfig()
        return VocalSeparationConfig(
            enabled = obj.optBoolean("enabled", false),
            model = VocalSeparationModel.byId(obj.optString("model")),
            hardwareAcceleration = obj.optBoolean("hardwareAcceleration", true),
            mix = VocalSeparationMix(
                obj.optInt("mixPosition", -1).takeIf { it in 0..VocalSeparationMix.MAX_POSITION }
                    ?: when (obj.optString("mix")) {
                        "INSTRUMENTAL_ONLY" -> 0
                        "REDUCED_VOCALS" -> 5
                        "ORIGINAL" -> 10
                        "REDUCED_INSTRUMENTAL" -> 15
                        "VOCALS_ONLY" -> 20
                        else -> (obj.optDouble("vocalVolume", 1.0).coerceIn(0.0, 1.0) * 10)
                            .takeIf { it.isFinite() }?.roundToInt() ?: 10
                    },
            ),
        )
    }
}

/** 人声分离开关、模型、硬件加速和人声音量的持久化。 */
object VocalSeparationSettings {
    private const val PREFS = "vocal_separation"
    private const val KEY_CONFIG = "config"
    private var context: Context? = null
    private val mutable = MutableStateFlow(VocalSeparationConfig())
    val state = mutable.asStateFlow()

    val value: VocalSeparationConfig get() = mutable.value

    fun init(context: Context) {
        if (this.context != null) return
        synchronized(this) {
            if (this.context != null) return
            val appContext = context.applicationContext
            this.context = appContext
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            mutable.value = VocalSeparationConfigCodec.decode(prefs.getString(KEY_CONFIG, null))
        }
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
    fun setModel(model: VocalSeparationModel) = update { it.copy(model = model) }
    fun setHardwareAcceleration(enabled: Boolean) = update { it.copy(hardwareAcceleration = enabled) }
    fun setMix(mix: VocalSeparationMix) = update { it.copy(mix = mix) }

    @Synchronized
    private fun update(block: (VocalSeparationConfig) -> VocalSeparationConfig) {
        val next = block(mutable.value)
        if (next == mutable.value) return
        mutable.value = next
        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_CONFIG, VocalSeparationConfigCodec.encode(next))?.apply()
    }
}
