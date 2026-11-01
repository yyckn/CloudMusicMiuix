package com.hypermusic.miuix.ui.pages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color as ComposeColor
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import androidx.compose.ui.unit.sp
import com.hypermusic.miuix.data.api.NeteaseApi
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QrLoginPage(
    onBackClick: () -> Unit,
    onLoginSuccess: (String) -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    var showImport by remember { mutableStateOf(false) }
    val initialCredentialImport = remember { com.hypermusic.miuix.ui.scan.CredentialImportController.completed }
    val credentialImport = com.hypermusic.miuix.ui.scan.CredentialImportController.completed
    LaunchedEffect(credentialImport) {
        if (credentialImport != null && credentialImport != initialCredentialImport && credentialImport.second == "wy") onLoginSuccess("")
    }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenHeightDp < configuration.screenWidthDp
    var qrKey by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText by remember { mutableStateOf("正在获取二维码...") }
    var statusCode by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }

    // 获取二维码；重新生成后也重新启动状态轮询。
    LaunchedEffect(generation) {
        qrKey = ""
        qrBitmap = null
        statusCode = 0
        statusText = "正在获取二维码..."
        NeteaseApi.getLoginQrKey().fold(
            onSuccess = { key ->
                qrKey = key
                qrBitmap = generateQrCode(NeteaseApi.getQrCodeUrl(key))
                statusText = "请使用网易云音乐APP扫描二维码"
                statusCode = 801

                var loginSuccess = false
                while (!loginSuccess) {
                    delay(2000)
                    NeteaseApi.checkQrLoginStatus(key).fold(
                        onSuccess = { code ->
                            statusCode = code
                            when (code) {
                                800, 8821 -> {
                                    statusText = "二维码已过期，请刷新"
                                    loginSuccess = true
                                }
                                801 -> statusText = "请使用网易云音乐APP扫描二维码"
                                802 -> statusText = "请在手机上确认登录"
                                803 -> {
                                    statusText = "登录成功"
                                    loginSuccess = true
                                    NeteaseApi.getUserInfo().fold(
                                        onSuccess = { nickname -> onLoginSuccess(nickname) },
                                        onFailure = { onLoginSuccess("") }
                                    )
                                }
                            }
                        },
                        onFailure = { e ->
                            statusText = "检查登录状态失败: ${e.message}"
                        }
                    )
                }
            },
            onFailure = { e ->
                statusText = "获取二维码失败: ${e.message}"
            }
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val refresh = {
        if (statusCode == 800 || statusCode == 8821 || qrKey.isEmpty()) {
            qrKey = ""
            qrBitmap = null
            statusText = "正在获取二维码..."
            statusCode = 0
            generation++
        } else {
            coroutineScope.launch {
                statusText = "正在检查登录状态..."
                NeteaseApi.checkQrLoginStatus(qrKey).fold(
                    onSuccess = { code ->
                        statusCode = code
                        statusText = when (code) {
                            800, 8821 -> "二维码已过期，请刷新"
                            801 -> "请使用网易云音乐APP扫描二维码"
                            802 -> "请在手机上确认登录"
                            803 -> {
                                NeteaseApi.getUserInfo().fold(
                                    onSuccess = { onLoginSuccess(it) }, onFailure = { onLoginSuccess("") }
                                )
                                "登录成功"
                            }
                            else -> "未知状态: $code"
                        }
                    },
                    onFailure = { statusText = "检查登录状态失败: ${it.message}" }
                )
            }
        }
        Unit
    }
    LoginPageFrame(onBackClick = onBackClick, onRefreshClick = refresh) {
        QrLoginLayout(
            isLandscape = isLandscape, qrBitmap = qrBitmap, statusText = statusText, statusCode = statusCode,
            context = context, onImportClick = { showImport = true },
            onTimeout = { generation++ }
        )
        LoginCredentialSheet(
            show = showImport,
            description = "请粘贴网易云音乐登录凭据 JSON，或选择凭据文件。",
            onDismiss = { showImport = false },
            onImport = { validateAndImportCredentials(it) },
            onSuccess = onLoginSuccess
        )
    }
}

@Composable
internal fun LoginPageFrame(onBackClick: () -> Unit, onRefreshClick: () -> Unit, content: @Composable () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    BackHandler { onBackClick() }
    val blurBackdrop = rememberPageBlurBackdrop()
    Scaffold(topBar = {
        BlurredBar(backdrop = blurBackdrop) {
            SmallTopAppBar(
                title = "登录账号",
                color = if (blurBackdrop != null) ComposeColor.Transparent else MiuixTheme.colorScheme.surface,
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                        modifier = Modifier.padding(start = 8.dp).clickable { onBackClick() })
                },
                actions = {
                    IconButton(onClick = onRefreshClick) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "刷新登录状态")
                    }
                }
            )
        }
    }) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())
            .overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection).pageLayerBackdrop(blurBackdrop)) {
            content()
        }
    }
}

@Composable
internal fun QrLoginLayout(
    isLandscape: Boolean,
    qrBitmap: Bitmap?,
    statusText: String,
    statusCode: Int,
    context: Context,
    onImportClick: () -> Unit,
    onTimeout: () -> Unit
) {
    // The providers do not supply a TTL. Replace the code after a conservative
    // client-side lifetime; an earlier server expiry always takes precedence.
    val deadline = remember(qrBitmap) { if (qrBitmap != null) android.os.SystemClock.elapsedRealtime() + 120_000L else null }
    var secondsRemaining by remember(deadline) { mutableIntStateOf(if (deadline == null) 0 else 120) }
    val timeout by androidx.compose.runtime.rememberUpdatedState(onTimeout)
    LaunchedEffect(deadline, statusCode) {
        if (deadline != null && statusCode != 800 && statusCode != 8821 && statusCode != 803) {
            while (true) {
                val remaining = deadline - android.os.SystemClock.elapsedRealtime()
                secondsRemaining = ((remaining.coerceAtLeast(0L) + 999L) / 1000L).toInt()
                if (remaining <= 0L) { timeout(); break }
                delay(minOf(remaining, 1000L))
            }
        }
    }
    if (isLandscape) {
        Row(Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                QrCodeWithCountdown(qrBitmap, secondsRemaining, statusCode)
            }
            Column(Modifier.weight(1f).padding(start = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                StatusSection(statusText, statusCode, qrBitmap, context, onImportClick)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            QrCodeWithCountdown(qrBitmap, secondsRemaining, statusCode)
            Spacer(Modifier.height(24.dp))
            StatusSection(statusText, statusCode, qrBitmap, context, onImportClick)
        }
    }
}

@Composable
private fun QrCodeWithCountdown(qrBitmap: Bitmap?, secondsRemaining: Int, statusCode: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        QrCodeBox(qrBitmap)
        if (qrBitmap != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (statusCode == 800 || statusCode == 8821) "二维码已过期" else "二维码有效期剩余${secondsRemaining}秒",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun QrCodeBox(qrBitmap: Bitmap?) {
    Box(
        modifier = Modifier
            .size(240.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        qrBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "登录二维码",
                modifier = Modifier.fillMaxSize()
            )
        } ?: run {
            Text(
                text = "加载中...",
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun StatusSection(
    statusText: String,
    statusCode: Int,
    qrBitmap: Bitmap?,
    context: Context,
    onImportClick: () -> Unit
) {
    Text(
        text = statusText,
        fontSize = 14.sp,
        color = when (statusCode) {
            803 -> MiuixTheme.colorScheme.primary
            800 -> MiuixTheme.colorScheme.error
            else -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    // 保存二维码图片到相册
    Button(
        onClick = {
            qrBitmap?.let { bitmap ->
                try {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "qr_code_${System.currentTimeMillis()}.png")
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/CloudMusic")
                    }
                    val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        try {
                            val saved = context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            } ?: false
                            check(saved) { "无法写入图片" }
                            Toast.makeText(context, "已保存到 /Pictures/CloudMusic/ 目录", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            context.contentResolver.delete(uri, null, null)
                            throw e
                        }
                    } else {
                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(context, "二维码尚未加载", Toast.LENGTH_SHORT).show()
            }
        },
        colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "保存二维码图片到相册")
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(onClick = onImportClick, modifier = Modifier.fillMaxWidth()) {
        Text("导入登录信息")
    }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra(com.hypermusic.miuix.ui.scan.QrScannerActivity.EXTRA_RESULT)?.let {
                com.hypermusic.miuix.ui.scan.CredentialImportController.submit(it)
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = { scanner.launch(android.content.Intent(context, com.hypermusic.miuix.ui.scan.QrScannerActivity::class.java)) },
        modifier = Modifier.fillMaxWidth()) { Text("扫码用户数据二维码") }
}

private suspend fun validateAndImportCredentials(jsonString: String): Result<String> {
    return try {
        val json = JsonParser.parseString(jsonString).asJsonObject

        // 验证字段：只允许包含指定字段
        val allowedFields = setOf("MUSIC_A_T", "MUSIC_R_T", "MUSIC_SNS", "MUSIC_U", "NMTID", "__csrf")
        val actualFields = json.keySet()
        if (!actualFields.all { it in allowedFields }) {
            return Result.failure(Exception("凭据中包含不支持的字段"))
        }

        // 验证MUSIC_U字段存在且不为空
        if (!json.has("MUSIC_U") || json.get("MUSIC_U").asString.isBlank()) {
            return Result.failure(Exception("凭据缺少 MUSIC_U"))
        }

        // 构建cookie字符串
        val cookieBuilder = StringBuilder()
        json.entrySet().forEach { entry ->
            if (cookieBuilder.isNotEmpty()) {
                cookieBuilder.append("; ")
            }
            cookieBuilder.append("${entry.key}=${entry.value.asString}")
        }
        val cookies = cookieBuilder.toString()

        // 设置cookie并验证登录
        val validation = withContext(Dispatchers.IO) {
            val profileResult = NeteaseApi.getUserProfile(cookies)
            profileResult.fold(
                onSuccess = { profile ->
                    if (profile.userId > 0) {
                        withContext(Dispatchers.Main) { NeteaseApi.commitImportedAccount(cookies, profile) }
                        Result.success(profile.nickname)
                    } else {
                        Result.failure(Exception("登录失败，请检查凭据是否有效"))
                    }
                },
                onFailure = { e ->
                    Result.failure(e)
                }
            )
        }
        validation
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun generateQrCode(content: String): Bitmap? {
    return try {
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
