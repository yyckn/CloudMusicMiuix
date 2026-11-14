package com.hypermusic.miuix.ui.pages

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountLabelsTest {
    private fun account(id: String, name: String, label: String? = null) = AccountInfo(id, name, "", label)

    @Test fun distinctNamesDoNotExposeIds() {
        assertEquals(listOf("甲", "乙"), accountOptionLabels(listOf(account("123456", "甲"), account("987654", "乙"))))
    }

    @Test fun duplicateNamesOnlyAppendMaskedIdsToDuplicates() {
        assertEquals(listOf("甲（ID：**1234）", "甲（ID：5678）", "乙"), accountOptionLabels(listOf(
            account("991234", "甲"), account("5678", "甲"), account("888888", "乙"))))
    }

    @Test fun differentQqLoginTypesDoNotAppendIds() {
        assertEquals(listOf("甲（微信）", "甲（QQ）", "乙（QQ）"), accountOptionLabels(listOf(
            account("995678", "甲", "微信登录"), account("123", "甲", "QQ登录"), account("654321", "乙", "QQ登录"))))
    }

    @Test fun duplicateNamesWithinLoginTypeAppendIdsOnlyToThatType() {
        assertEquals(listOf("甲（微信）", "甲（QQ）（ID：123）", "甲（QQ）（ID：**4321）"), accountOptionLabels(listOf(
            account("995678", "甲", "微信登录"), account("123", "甲", "QQ登录"), account("654321", "甲", "QQ登录"))))
    }

    @Test fun duplicateWechatNamesAppendIds() {
        assertEquals(listOf("甲（微信）（ID：**5678）", "甲（微信）（ID：123）"), accountOptionLabels(listOf(
            account("995678", "甲", "微信登录"), account("123", "甲", "微信登录"))))
    }
}
