package com.hypermusic.miuix.ui.scan

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.AccountImportPolicy
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.data.api.UserCredentialLink
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.overlay.OverlayDialog

object CredentialImportController {
    var incoming by mutableStateOf<String?>(null)
        private set
    var completed by mutableStateOf<Pair<Long, String>?>(null)
        private set
    private var counter = 0L
    fun submit(value: String) { incoming = value }
    fun consume() { incoming = null }
    fun markCompleted(platform: String) { completed = ++counter to platform }
}

@Composable
fun CredentialImportHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<UserCredentialLink.Payload?>(null) }
    var conflict by remember { mutableStateOf(false) }
    var confirmingReplace by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val incoming = CredentialImportController.incoming
    fun execute(replace: Boolean, enableMulti: Boolean) {
        val payload = pending ?: return
        if (busy) return
        busy = true
        scope.launch {
            val result = if (payload.platform == "qq") {
                QqMusicApi.importCredential(payload.credential, replace, enableMulti)
            } else runCatching {
                val fields = JsonParser.parseString(payload.credential).asJsonObject
                val cookie = fields.entrySet().joinToString("; ") { "${it.key}=${it.value.asString}" }
                val profile = NeteaseApi.getUserProfile(cookie).getOrThrow()
                require(profile.userId > 0) { "登录凭证已失效" }
                NeteaseApi.commitImportedAccount(cookie, profile, replace, enableMulti)
                profile.nickname
            }
            busy = false
            result.fold(onSuccess = {
                val platform = payload.platform
                pending = null; conflict = false; confirmingReplace = false; error = null
                CredentialImportController.markCompleted(platform)
                Toast.makeText(context, "账号导入成功", Toast.LENGTH_SHORT).show()
            }, onFailure = {
                confirmingReplace = false
                if (conflict) error = "导入失败，请检查凭证或网络连接后重试"
                else { pending = null; Toast.makeText(context, "导入失败，请检查凭证或网络连接", Toast.LENGTH_LONG).show() }
            })
        }
    }
    LaunchedEffect(incoming) {
        if (incoming == null) return@LaunchedEffect
        val input = incoming
        CredentialImportController.consume()
        if (pending != null || busy) {
            Toast.makeText(context, "请先处理当前导入请求", Toast.LENGTH_SHORT).show()
            return@LaunchedEffect
        }
        val parsed = runCatching { UserCredentialLink.decode(input) }.getOrNull()
        if (parsed == null) {
            Toast.makeText(context, "不是有效的 HyperMusic 用户数据二维码", Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }
        pending = parsed
        val prefs = context.getSharedPreferences(if (parsed.platform == "qq") "qqmusic_prefs" else "netease_prefs", android.content.Context.MODE_PRIVATE)
        val hasAccount = if (parsed.platform == "qq") QqMusicApi.accounts.isNotEmpty() || QqMusicApi.isLoggedIn()
            else NeteaseApi.getAccounts().isNotEmpty() || NeteaseApi.isLoggedIn()
        conflict = AccountImportPolicy.requiresChoice(hasAccount, prefs.getBoolean("multi_account_enabled", false))
        error = null
        if (!conflict) execute(replace = false, enableMulti = false)
    }
    BackHandler(enabled = conflict || confirmingReplace || busy) { }
    val platformName = if (pending?.platform == "qq") "QQ音乐" else "网易云音乐"
    OverlayDialog(show = busy && !conflict, title = "正在导入账号", summary = "正在验证登录凭证，请稍候。",
        onDismissRequest = {}, content = { InfiniteProgressIndicator() })
    OverlayDialog(show = conflict, title = "是否继续导入",
        summary = "您正在导入${platformName}平台的账号，但当前您未开启多账号切换，所以只能存储一个账号，请选择导入方式：" + (error?.let { "\n\n$it" } ?: ""),
        onDismissRequest = {}, content = {
            Column(Modifier.fillMaxWidth()) {
                TextButton(text = if (busy) "正在导入…" else "打开多账号切换并新增此账号（推荐）", enabled = !busy,
                    onClick = { execute(replace = false, enableMulti = true) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary())
                Spacer(Modifier.height(8.dp))
                TextButton(text = "替换旧账号", enabled = !busy, onClick = { confirmingReplace = true }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextButton(text = "取消", enabled = !busy, onClick = { conflict = false; pending = null; error = null }, modifier = Modifier.fillMaxWidth())
            }
        })
    OverlayDialog(show = confirmingReplace, title = "确认替换旧账号？",
        summary = "导入成功后，该平台原有账号将从本机移除。", onDismissRequest = { confirmingReplace = false }, content = {
            Row(Modifier.fillMaxWidth()) {
                TextButton(text = "取消", enabled = !busy, onClick = { confirmingReplace = false }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                TextButton(text = if (busy) "正在导入…" else "确认替换", enabled = !busy,
                    onClick = { execute(replace = true, enableMulti = false) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        })
}
