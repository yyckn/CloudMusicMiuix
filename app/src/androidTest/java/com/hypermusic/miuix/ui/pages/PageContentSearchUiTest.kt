package com.hypermusic.miuix.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.hypermusic.miuix.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class PageContentSearchUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toolbarSearchAcceptsQueryAndCancelRestoresUnfilteredState() {
        val state = PageContentSearchState()
        compose.setContent {
            MiuixTheme {
                Column { PageContentSearchAction(state); PageContentSearchBar(state) }
            }
        }
        compose.onNodeWithContentDescription("搜索页面内容").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("周杰伦 晴天")
        compose.runOnIdle { assertEquals("周杰伦 晴天", state.query); assertTrue(state.active) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertFalse(state.active); assertEquals("", state.query) }
    }
}
