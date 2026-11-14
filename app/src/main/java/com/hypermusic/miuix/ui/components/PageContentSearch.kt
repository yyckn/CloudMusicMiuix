package com.hypermusic.miuix.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search

@Stable
internal class PageContentSearchState(active: Boolean = false, query: String = "") {
    var active by mutableStateOf(active)
    var query by mutableStateOf(query)
    fun close() { active = false; query = "" }
}

@Composable
internal fun rememberPageContentSearch(key: String): PageContentSearchState = rememberSaveable(key,
    saver = listSaver(save = { listOf(it.active, it.query) }, restore = { PageContentSearchState(it[0] as Boolean, it[1] as String) })
) { PageContentSearchState() }

private val queryWhitespace = Regex("\\s+")

internal fun matchesPageQuery(query: String, vararg fields: String): Boolean {
    val text = fields.joinToString(" ")
    return query.trim().split(queryWhitespace).all { it.isBlank() || text.contains(it, ignoreCase = true) }
}

@Composable
internal fun PageContentSearchAction(state: PageContentSearchState) {
    IconButton(onClick = { state.active = true }) {
        Icon(MiuixIcons.Basic.Search, contentDescription = "搜索页面内容")
    }
}

/** Miuix SearchBar/InputField with its standard outside cancel action; results stay in this page. */
@Composable
internal fun PageContentSearchBar(state: PageContentSearchState, label: String = "搜索页面内的音乐") {
    if (!state.active) return
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun close() { keyboard?.hide(); focus.clearFocus(); state.close() }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        requester.requestFocus()
        keyboard?.show()
    }
    SearchBar(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        expanded = true,
        onExpandedChange = { if (!it) close() },
        inputField = {
            InputField(query = state.query, onQueryChange = { state.query = it },
                onSearch = { keyboard?.hide(); focus.clearFocus() }, expanded = true,
                onExpandedChange = { if (!it) close() }, label = label,
                modifier = Modifier.focusRequester(requester))
        },
        outsideEndAction = { TextButton(text = "取消", modifier = Modifier.padding(end = 12.dp), onClick = { close() }) }
    ) {}
}
