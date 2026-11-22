package com.hypermusic.miuix.ui.pages

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import com.hypermusic.miuix.ui.components.*
import org.junit.Assert.*
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class PageContentSearchUiTest {
    @Test fun toolbarSearchAcceptsQueryAndCancelRestoresUnfilteredState() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue(context.packageName.endsWith(".glasstest") && android.os.Build.VERSION.SDK_INT >= 29)
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ComponentActivity
        val state = PageContentSearchState()
        var downloadsOpened = false
        fun nodes(): List<SemanticsNode> {
            val result = mutableListOf<SemanticsNode>()
            fun read(node: SemanticsNode) { result += node; node.children.forEach(::read) }
            fun visit(view: android.view.View) {
                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" }?.let {
                    read((it.invoke(view) as SemanticsOwner).rootSemanticsNode)
                }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach(::visit)
            return result
        }
        fun click(predicate: (SemanticsNode) -> Boolean) {
            instrumentation.runOnMainSync {
                var target: SemanticsNode? = nodes().first(predicate)
                while (target != null && target.config.getOrNull(SemanticsActions.OnClick) == null) target = target.parent
                assertTrue(requireNotNull(target).config[SemanticsActions.OnClick].action!!.invoke())
            }
            Thread.sleep(500)
        }
        fun text(value: String): (SemanticsNode) -> Boolean = { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == value } == true
        }
        fun more() = click { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("更多") == true }
        try {
            instrumentation.runOnMainSync { activity.setContent {
                MiuixTheme {
                    top.yukonga.miuix.kmp.basic.Scaffold { _ ->
                        Column { PageContentMenu(state, emptyList(), onShowDownloads = { downloadsOpened = true }); PageContentSearchBar(state) }
                    }
                }
            } }
            Thread.sleep(650)
            more()
            click(text("搜索当前页面内容"))
            instrumentation.runOnMainSync {
                assertTrue(state.active)
                assertTrue(nodes().first { it.config.getOrNull(SemanticsActions.SetText) != null }
                    .config[SemanticsActions.SetText].action!!.invoke(AnnotatedString("周杰伦 晴天")))
                assertEquals("周杰伦 晴天", state.query)
            }
            click(text("取消"))
            instrumentation.runOnMainSync { assertFalse(state.active); assertEquals("", state.query) }
            more()
            click(text("下载任务"))
            instrumentation.runOnMainSync { assertTrue(downloadsOpened) }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
