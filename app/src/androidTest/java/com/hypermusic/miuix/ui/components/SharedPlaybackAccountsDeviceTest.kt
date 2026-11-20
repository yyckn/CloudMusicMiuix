package com.hypermusic.miuix.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.gson.Gson
import com.hypermusic.miuix.data.api.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses synthetic credentials only and restores every modified preference in finally. */
@RunWith(AndroidJUnit4::class)
class SharedPlaybackAccountsDeviceTest {
    @Test fun selectionsSurviveRestartAndNeverChangeEitherDataAccount() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".glasstest"))
        val names = listOf("qqmusic_prefs", "bodian_prefs", "additional_accounts_BODIAN", "shared_playback_accounts")
        val snapshots = names.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        try {
            names.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
            val qq = listOf(
                QqMusicApi.Account(QqMusicApi.Credential("test-qq", "test-only", "", 2), "相同昵称"),
                QqMusicApi.Account(QqMusicApi.Credential("test-wx", "test-only", "", 1), "相同昵称"),
            )
            context.getSharedPreferences("qqmusic_prefs", Context.MODE_PRIVATE).edit()
                .putString("accounts", Gson().toJson(qq)).putString("data_account_id", "test-qq")
                .putString("playback_account_id", "test-qq").putBoolean("multi_account_enabled", true).commit()
            context.getSharedPreferences("bodian_prefs", Context.MODE_PRIVATE).edit()
                .putString("uid", "test-bd").putString("token", "test-only").putString("nickname", "波点测试账号").commit()
            instrumentation.runOnMainSync { QqMusicApi.init(context) }
            val store = AdditionalAccounts(context, MusicPlatform.BODIAN)
            instrumentation.runOnMainSync { store.enabled = true }
            val bd = SharedPlaybackAccounts.Selection("bodian", "test-bd")
            val wx = SharedPlaybackAccounts.Selection("qqmusic", "test-wx")
            val qqOptions = SharedPlaybackAccounts.options(context, "qqmusic")
            assertEquals(3, qqOptions.size)
            assertTrue(qqOptions.any { it.label == "波点测试账号（波点）" })
            val bdOptions = SharedPlaybackAccounts.options(context, "bodian")
            assertTrue(bdOptions.any { it.label == "相同昵称（QQ音乐-QQ）" })
            assertTrue(bdOptions.any { it.label == "相同昵称（QQ音乐-微信）" })
            instrumentation.runOnMainSync {
                SharedPlaybackAccounts.select(context, "qqmusic", bd)
                SharedPlaybackAccounts.select(context, "bodian", wx)
            }
            assertEquals("test-qq", QqMusicApi.dataAccountId)
            assertEquals("test-bd", store.dataId)
            assertEquals("test-qq", QqMusicApi.playbackAccountId)
            assertEquals("test-bd", store.playbackId)
            // An explicit source account is used without temporarily switching the data
            // account or that platform's own playback selection.
            instrumentation.runOnMainSync {
                context.getSharedPreferences("bodian_prefs", Context.MODE_PRIVATE).edit()
                    .putString("uid", "test-bd2").putString("token", "test-token-2").putString("nickname", "第二个波点账号").commit()
                store.captureLogin()
            }
            val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
                assertEquals("test-bd2", chain.request().header("uid"))
                assertEquals("test-token-2", chain.request().header("token"))
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200).message("OK").body("{\"code\":200,\"data\":{}}".toByteArray().let {
                        okhttp3.ResponseBody.create(null, it)
                    }).build()
            }.build()
            BodianClient(context, client, playback = true, playbackAccountIdOverride = "test-bd2").call("/test")
            assertEquals("test-bd", store.dataId)
            assertEquals("test-bd", store.playbackId)
            instrumentation.uiAutomation.executeShellCommand("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
                .use { java.io.FileInputStream(it.fileDescriptor).readBytes() }
            val activity = instrumentation.startActivitySync(Intent(context, ComponentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ComponentActivity
            try {
                instrumentation.runOnMainSync { activity.setContent {
                    top.yukonga.miuix.kmp.theme.MiuixTheme {
                        com.hypermusic.miuix.ui.pages.AdditionalProfilePage(MusicPlatform.BODIAN, "test-bd2", {}, {})
                    }
                } }
                Thread.sleep(650)
                instrumentation.runOnMainSync {
                    val texts = mutableListOf<String>()
                    fun visit(node: androidx.compose.ui.semantics.SemanticsNode) {
                        texts += node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
                        node.children.forEach(::visit)
                    }
                    fun views(view: android.view.View) {
                        view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" }?.let {
                            visit((it.invoke(view) as SemanticsOwner).rootSemanticsNode)
                        }
                        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) views(view.getChildAt(i))
                    }
                    views(activity.window.decorView)
                    assertTrue(texts.contains("第二个波点账号"))
                    assertTrue(texts.contains("用户ID"))
                    assertEquals("Opening another profile must not switch the data account", "test-bd", store.dataId)
                }
            } finally { instrumentation.runOnMainSync { activity.finish() } }
            instrumentation.runOnMainSync { QqMusicApi.init(context) }
            assertEquals(bd, SharedPlaybackAccounts.current(context, "qqmusic"))
            assertEquals(wx, SharedPlaybackAccounts.current(context, "bodian"))
            instrumentation.runOnMainSync { store.remove("test-bd") }
            assertNull(SharedPlaybackAccounts.selected(context, "qqmusic"))
            assertEquals(SharedPlaybackAccounts.Selection("qqmusic", "test-qq"), SharedPlaybackAccounts.current(context, "qqmusic"))
            instrumentation.runOnMainSync { SharedPlaybackAccounts.select(context, "bodian", wx) }
            assertEquals("test-qq", QqMusicApi.dataAccountId)
        } finally {
            snapshots.forEach { (name, values) ->
                val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                values.forEach { (key, value) -> when (value) {
                    is String -> edit.putString(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                } }
                check(edit.commit())
            }
            instrumentation.runOnMainSync { QqMusicApi.init(context); AdditionalAccounts.revision.intValue++; SharedPlaybackAccounts.revision.intValue++ }
        }
    }
}
