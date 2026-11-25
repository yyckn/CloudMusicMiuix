package com.hypermusic.miuix.data.audio.separation

import ai.onnxruntime.OrtProvider
import com.hypermusic.miuix.data.audio.separation.VocalSeparationSeparator.InferenceBackend
import org.junit.Assert.assertEquals
import org.junit.Test

class VocalSeparationBackendTest {
    private val providers = setOf(OrtProvider.WEBGPU, OrtProvider.NNAPI, OrtProvider.CPU)

    @Test
    fun supportedSocFamiliesTryWebGpuBeforeOtherBackends() {
        val models = listOf(
            "SM8475", "SM8550-AC", "SM8650", "SM8750P", " sm8850 ", "SM8845",
            "SM8950", "SM8975", "SM8635", "SM8735",
            "SM7475-AB", "SM7675-AB", "SM7550", "SM7750", "SM7435", "SM7635-AC",
        )
        val expected = listOf(InferenceBackend.WEBGPU, InferenceBackend.NNAPI, InferenceBackend.CPU)
        for (model in models) {
            assertEquals(model, expected, VocalSeparationSeparator.inferenceBackends(model, true, providers))
        }
    }

    @Test
    fun skipsWebGpuWithoutMatchingSocVulkanOrProvider() {
        val expected = listOf(InferenceBackend.NNAPI, InferenceBackend.CPU)
        for (model in listOf("SM8450", "SM8250", "SM87500", "MT6991", "unknown")) {
            assertEquals(model, expected, VocalSeparationSeparator.inferenceBackends(model, true, providers))
        }
        assertEquals(expected, VocalSeparationSeparator.inferenceBackends("SM8475", false, providers))
        assertEquals(
            expected,
            VocalSeparationSeparator.inferenceBackends("SM8475", true, providers - OrtProvider.WEBGPU),
        )
    }

    @Test
    fun fallsBackToCpuWhenNnapiAndWebGpuAreUnavailable() {
        assertEquals(
            listOf(InferenceBackend.CPU),
            VocalSeparationSeparator.inferenceBackends("SM7475", true, setOf(OrtProvider.CPU)),
        )
    }
}
