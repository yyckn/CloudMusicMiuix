package com.hypermusic.miuix.ui.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Shared credential import drawer for each music provider. */
@Composable
internal fun LoginCredentialSheet(
    show: Boolean,
    description: String,
    onDismiss: () -> Unit,
    onImport: suspend (String) -> Result<String>,
    onSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readNBytes(1024 * 1024 + 1)
                            require(bytes.size <= 1024 * 1024) { "文件过大，请选择不超过 1 MB 的凭据文件" }
                            bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                        } ?: error("无法打开文件")
                    }
                }
                result.fold({ input = it; error = null }, { error = "读取文件失败：${it.message}" })
                busy = false
            }
        }
    }
    OverlayBottomSheet(
        show = show,
        title = "导入登录信息",
        insideMargin = DpSize(12.dp, 0.dp),
        onDismissRequest = { if (!busy) onDismiss() }
    ) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(description, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(12.dp))
            TextField(value = input, onValueChange = { input = it; error = null },
                label = "请粘贴登录凭据", useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth().height(150.dp))
            error?.let { Text(it, modifier = Modifier.padding(top = 8.dp), color = MiuixTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("选择凭据文件") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (input.isBlank()) { error = "请先输入登录凭据或选择文件"; return@Button }
                busy = true
                scope.launch {
                    val result = runCatching { onImport(input) }.getOrElse { Result.failure(it) }
                    busy = false
                    result.fold({ onDismiss(); onSuccess(it) }, { error = it.message ?: "登录凭据无效，请检查后重试" })
                }
            }, enabled = !busy, colors = ButtonDefaults.buttonColorsPrimary(), modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "正在处理…" else "导入并登录")
            }
        }
    }
}
