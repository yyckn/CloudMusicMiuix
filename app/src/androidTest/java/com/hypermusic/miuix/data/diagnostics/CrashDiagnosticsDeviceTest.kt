package com.hypermusic.miuix.data.diagnostics

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CrashDiagnosticsDeviceTest {
    @Test fun uncaughtExceptionInAnotherProcessIsPersistedAndCanBeViewed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        val token = UUID.randomUUID().toString()
        context.startActivity(Intent().setClassName(context.packageName, "com.hypermusic.miuix.DiagnosticCrashProbeActivity")
            .putExtra("diagnostic_token", token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var report: java.io.File? = null
        val deadline = android.os.SystemClock.elapsedRealtime() + 15_000
        while (report == null && android.os.SystemClock.elapsedRealtime() < deadline) {
            report = CrashDiagnostics.store.reports().firstOrNull { CrashDiagnostics.store.read(it).contains("diagnostic-crash-$token") }
            if (report == null) Thread.sleep(200)
        }
        val file = requireNotNull(report) { "The independently crashed process did not persist a report" }
        val text = CrashDiagnostics.store.read(file)
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(text.contains("lyrics-probe-$token"))
        assertTrue(text.contains("设备："))
        assertTrue(text.contains("采集版本："))
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        fun nodes(): List<SemanticsNode> {
            val result = mutableListOf<SemanticsNode>()
            fun read(node: SemanticsNode) { result += node; node.children.forEach(::read) }
            fun visit(view: android.view.View) {
                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" }?.let { read((it.invoke(view) as SemanticsOwner).rootSemanticsNode) }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            visit(activity.window.decorView)
            return result
        }
        try {
            instrumentation.runOnMainSync { activity.setContent {
                top.yukonga.miuix.kmp.theme.MiuixTheme {
                    top.yukonga.miuix.kmp.material.SoftGlassTheme(true, false) {
                        top.yukonga.miuix.kmp.basic.Scaffold { _ -> com.hypermusic.miuix.ui.components.CrashLogSheet(true) {} }
                    }
                }
            } }
            Thread.sleep(700)
            instrumentation.runOnMainSync {
                var target: SemanticsNode? = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "Java / Kotlin 未捕获异常" } == true }
                while (target != null && target.config.getOrNull(SemanticsActions.OnClick) == null) target = target.parent
                assertTrue(requireNotNull(target).config[SemanticsActions.OnClick].action!!.invoke())
            }
            Thread.sleep(700)
            instrumentation.runOnMainSync {
                assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "复制" } == true })
                assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == "分享日志" } == true })
            }
        } finally {
            file.delete()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
