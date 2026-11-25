package com.hypermusic.miuix.ui.pages

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.data.api.PlatformManager
import com.hypermusic.miuix.data.api.MusicPlatform
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import top.yukonga.miuix.kmp.material.SoftGlassTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

class ManageAccountsLayoutDeviceTest {
    @Test fun settingsEntryAndAccountCountRulesUseTheNewLayout() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        val prefs = context.getSharedPreferences("netease_prefs", 0)
        val existed = prefs.contains("multi_account_enabled")
        val oldEnabled = prefs.getBoolean("multi_account_enabled", false)
        val oldPlatform = PlatformManager.current
        val platformPrefs = context.getSharedPreferences("music_platform", 0)
        val savedPlatform = platformPrefs.getString("current", null)
        val settings = mutableStateOf(true)
        val accounts = mutableStateOf(listOf(AccountInfo("test-A", "测试账号A", "")))
        instrumentation.runOnMainSync { PlatformManager.select(MusicPlatform.NETEASE); prefs.edit().putBoolean("multi_account_enabled", false).commit() }
        instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
            .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
        val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ComponentActivity
        fun nodes(): List<SemanticsNode> {
            val result = mutableListOf<SemanticsNode>()
            fun read(node: SemanticsNode) { result += node; node.children.forEach(::read) }
            fun visit(view: android.view.View) {
                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" }?.let { read((it.invoke(view) as SemanticsOwner).rootSemanticsNode) }
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach(::visit)
            return result
        }
        fun has(text: String) = nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
        fun clickNode(target: SemanticsNode) {
            var node: SemanticsNode? = target
            while (node != null && node.config.getOrNull(SemanticsActions.OnClick) == null) node = node.parent
            assertTrue(requireNotNull(node).config[SemanticsActions.OnClick].action!!.invoke())
        }
        fun clickText(text: String) {
            instrumentation.runOnMainSync { clickNode(nodes().first { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }) }
            Thread.sleep(400)
        }
        fun toggle() {
            instrumentation.runOnMainSync { clickNode(nodes().first { it.config.getOrNull(SemanticsProperties.ToggleableState) != null }) }
            Thread.sleep(400)
        }
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    MiuixTheme { SoftGlassTheme(true, false) {
                        Scaffold { _ ->
                            if (settings.value) SettingsPage(isLoggedIn = false, onLoginClick = {}, onProfileClick = {},
                                onThemeSettingsClick = {}, onAboutClick = {}, onManageAccountsClick = { settings.value = false })
                            else ManageAccountsPage(accounts.value, {}, {}, {}, onPlaybackAccountChange = {}, onDataAccountChange = {})
                        }
                    } }
                }
            }
            Thread.sleep(1200)
            instrumentation.runOnMainSync {
                assertTrue(has("管理账号")); assertTrue(has("听歌记录"))
                assertFalse(has("点击登录")); assertFalse(has("多账号切换"))
            }
            clickText("管理账号")
            instrumentation.runOnMainSync {
                assertTrue(has("账号管理")); assertTrue(has("多账号切换"))
                assertFalse(has("登录新账号")); assertFalse(has("账号切换"))
            }
            toggle()
            instrumentation.runOnMainSync { assertTrue(has("登录新账号")); assertFalse(has("账号切换")) }
            instrumentation.runOnMainSync { accounts.value = accounts.value + AccountInfo("test-B", "测试账号B", "") }
            Thread.sleep(400)
            instrumentation.runOnMainSync { assertTrue(has("账号切换")); assertTrue(has("播放歌曲")); assertTrue(has("获取数据")) }
            toggle()
            instrumentation.runOnMainSync {
                assertTrue("Disabling multiple accounts must show the count warning", has("无法关闭多账号"))
                assertTrue("Rejected disable must retain the saved switch", prefs.getBoolean("multi_account_enabled", false))
            }
            clickText("确定")
            instrumentation.runOnMainSync { accounts.value = emptyList(); prefs.edit().putBoolean("multi_account_enabled", false).commit() }
            Thread.sleep(400)
            instrumentation.runOnMainSync {
                assertTrue(has("多账号切换")); assertTrue(has("登录新账号")); assertFalse(has("账号切换"))
            }
        } finally {
            instrumentation.runOnMainSync {
                activity.finish()
                val edit = prefs.edit()
                if (existed) edit.putBoolean("multi_account_enabled", oldEnabled) else edit.remove("multi_account_enabled")
                edit.commit()
                PlatformManager.select(oldPlatform)
                if (savedPlatform == null) platformPrefs.edit().remove("current").commit()
                else platformPrefs.edit().putString("current", savedPlatform).commit()
            }
        }
    }
}
