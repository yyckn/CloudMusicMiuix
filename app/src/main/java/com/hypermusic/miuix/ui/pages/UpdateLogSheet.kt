package com.hypermusic.miuix.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import com.hypermusic.miuix.data.model.sections
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 更新日志抽屉。需放在有 Scaffold 的页面中使用（依赖 Scaffold 的弹层宿主）。
 */
@Composable
fun UpdateLogSheet(viewModel: MusicPlayerViewModel) {
    val updateInfo = viewModel.selectedUpdateLog ?: return
    val scrollState = remember(updateInfo.version) { ScrollState(0) }
    val sections = remember(updateInfo) { updateInfo.sections() }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    OverlayBottomSheet(
        show = viewModel.showUpdateLog,
        title = "更新日志",
        insideMargin = DpSize(12.dp, 0.dp),
        startAction = null,
        endAction = {
            IconButton(onClick = { viewModel.closeUpdateLog() }) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = "关闭",
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        },
        onDismissRequest = { viewModel.closeUpdateLog() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The sheet measures its natural content height and bounds it to the window.
                .verticalScroll(scrollState)
                .padding(bottom = 16.dp + bottomInset)
        ) {
            Text(
                text = "v${updateInfo.version}",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onSurface
            )
            if (updateInfo.date.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "发布于 ${updateInfo.date}",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            sections.forEach { section ->
                section.title?.let { SmallTitle(it) }
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        section.lines.forEachIndexed { index, line ->
                            if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                            Text(text = line, style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

        }
    }
}
