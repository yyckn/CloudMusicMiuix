package com.hypermusic.miuix.ui.pages

import com.hypermusic.miuix.data.model.radioModeLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioModeLabelTest {
    @Test fun qishuiCardsHaveTheirOwnModeLabel() {
        assertEquals("私人FM", radioModeLabel("extra:qishui:fm", "私人FM"))
        assertEquals("专注模式", radioModeLabel("extra:qishui:scene:6", "专注模式"))
        assertEquals("场景推荐 (动感健身)", radioModeLabel("extra:qishui:scene:9", "动感健身"))
        assertEquals("场景推荐 (驾车)", radioModeLabel("extra:qishui:scene:33", "驾车"))
        assertEquals("私人FM", radioModeLabel("extra:bodian:fm", "私人FM"))
        assertEquals("私人FM", radioModeLabel("extra:kugou:fm", "私人FM"))
    }

    @Test fun qualityAndModeAreComposedAcrossLayoutsAndRespectQualitySwitch() {
        assertEquals("场景推荐 (动感健身) · 无损", getQualityText(0, "无损", isFMMode = true,
            fmModeLabel = "场景推荐 (动感健身)"))
        assertEquals("私人FM · 无损", getQualityText(0, "无损", isFMMode = true))
        assertEquals("专注模式 · 正在加载", getQualityText(0, "正在加载", isFMMode = true, fmModeLabel = "专注模式"))
        assertEquals("场景推荐 (动感健身)", getQualityText(0, "无损", isFMMode = true,
            showQuality = false, fmModeLabel = "场景推荐 (动感健身)"))
        assertEquals("无损", getQualityText(0, "无损", fmModeLabel = "场景推荐 (驾车)"))
        assertEquals("心动模式 · 无损", getQualityText(0, "无损", isIntelligenceMode = true))
    }
}
