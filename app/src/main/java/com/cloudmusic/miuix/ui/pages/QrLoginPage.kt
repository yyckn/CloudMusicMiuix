package com.cloudmusic.miuix.ui.pages

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import com.cloudmusic.miuix.data.api.NeteaseApi
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    val scrollBehavior = MiuixScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("扫码登录", "导入登录信息")

    // 判断是否是横向布局
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenHeightDp < configuration.screenWidthDp

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "登录方式",
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onBackClick() }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            // TabRow
            TabRow(
                tabs = tabTitles,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            when (selectedTab) {
                0 -> QrLoginContent(
                    isLandscape = isLandscape,
                    onLoginSuccess = onLoginSuccess,
                    context = context
                )
                1 -> ImportLoginContent(
                    onLoginSuccess = onLoginSuccess,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun QrLoginContent(
    isLandscape: Boolean,
    onLoginSuccess: (String) -> Unit,
    context: Context
) {
    val coroutineScope = rememberCoroutineScope()
    var qrKey by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText by remember { mutableStateOf("正在获取二维码...") }
    var statusCode by remember { mutableIntStateOf(0) }

    // 获取二维码
    LaunchedEffect(Unit) {
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

    if (isLandscape) {
        // 横向布局：左右分屏
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：二维码
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                QrCodeBox(qrBitmap)
            }

            // 右侧：状态和按钮
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StatusSection(statusText, statusCode, qrKey, qrBitmap, context) { newKey, newBitmap, newStatus, newCode ->
                    qrKey = newKey
                    qrBitmap = newBitmap
                    statusText = newStatus
                    statusCode = newCode
                }
            }
        }
    } else {
        // 纵向布局：上下排列
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QrCodeBox(qrBitmap)
            Spacer(modifier = Modifier.height(24.dp))
            StatusSection(statusText, statusCode, qrKey, qrBitmap, context) { newKey, newBitmap, newStatus, newCode ->
                qrKey = newKey
                qrBitmap = newBitmap
                statusText = newStatus
                statusCode = newCode
            }
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
    qrKey: String,
    qrBitmap: Bitmap?,
    context: Context,
    onUpdate: (String, Bitmap?, String, Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

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

    // 刷新按钮
    Button(
        onClick = {
            coroutineScope.launch {
                if (statusCode == 800 || statusCode == 8821) {
                    // 二维码过期，重新获取
                    onUpdate("", null, "正在获取二维码...", 0)
                    NeteaseApi.getLoginQrKey().fold(
                        onSuccess = { key ->
                            val bitmap = generateQrCode(NeteaseApi.getQrCodeUrl(key))
                            onUpdate(key, bitmap, "请使用网易云音乐APP扫描二维码", 801)
                        },
                        onFailure = { e ->
                            onUpdate("", null, "获取二维码失败: ${e.message}", 0)
                        }
                    )
                } else if (qrKey.isNotEmpty()) {
                    // 手动刷新登录状态
                    onUpdate(qrKey, qrBitmap, "正在检查登录状态...", statusCode)
                    NeteaseApi.checkQrLoginStatus(qrKey).fold(
                        onSuccess = { code ->
                            val newStatus = when (code) {
                                800, 8821 -> "二维码已过期，请刷新"
                                801 -> "请使用网易云音乐APP扫描二维码"
                                802 -> "请在手机上确认登录"
                                803 -> {
                                    NeteaseApi.getUserInfo().fold(
                                        onSuccess = { nickname -> },
                                        onFailure = { }
                                    )
                                    "登录成功"
                                }
                                else -> "未知状态: $code"
                            }
                            onUpdate(qrKey, qrBitmap, newStatus, code)
                        },
                        onFailure = { e ->
                            onUpdate(qrKey, qrBitmap, "检查登录状态失败: ${e.message}", statusCode)
                        }
                    )
                }
            }
        },
        colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (statusCode == 800 || statusCode == 8821) "刷新二维码" else "刷新登录状态",
            color = MiuixTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun ImportLoginContent(
    onLoginSuccess: (String) -> Unit,
    context: Context
) {
    var importText by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // 文件选择器 - 使用 ACTION_OPEN_DOCUMENT
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val content = inputStream?.bufferedReader().use { reader -> reader?.readText() }
                if (content != null) {
                    importText = content
                }
            } catch (e: Exception) {
                Toast.makeText(context, "读取文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 输入框
        TextField(
            value = importText,
            onValueChange = { importText = it },
            label = "请粘贴完整登录凭据",
            useLabelAsPlaceholder = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 导入登录凭据按钮
        Button(
            onClick = {
                if (importText.isBlank()) {
                    Toast.makeText(context, "请先输入登录凭据", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isImporting = true
                coroutineScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        validateAndImportCredentials(importText)
                    }
                    isImporting = false
                    result.fold(
                        onSuccess = { nickname ->
                            Toast.makeText(context, "登录成功", Toast.LENGTH_SHORT).show()
                            onLoginSuccess(nickname)
                        },
                        onFailure = { e ->
                            Toast.makeText(context, "数据错误", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isImporting
        ) {
            if (isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(text = "导入登录凭据")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 导入登录凭据文件按钮
        Button(
            onClick = {
                filePickerLauncher.launch(arrayOf("application/json"))
            },
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "导入登录凭据文件（loginkey.json）")
        }
    }
}

private suspend fun validateAndImportCredentials(jsonString: String): Result<String> {
    return try {
        val json = JsonParser.parseString(jsonString).asJsonObject

        // 验证字段：只允许包含指定字段
        val allowedFields = setOf("MUSIC_A_T", "MUSIC_R_T", "MUSIC_SNS", "MUSIC_U", "NMTID", "__csrf")
        val actualFields = json.keySet()
        if (!actualFields.all { it in allowedFields }) {
            return Result.failure(Exception("Invalid fields"))
        }

        // 验证MUSIC_U字段存在且不为空
        if (!json.has("MUSIC_U") || json.get("MUSIC_U").asString.isBlank()) {
            return Result.failure(Exception("MUSIC_U is missing or empty"))
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
        NeteaseApi.setCookie(cookies)

        // 尝试获取用户资料来验证登录是否成功
        withContext(Dispatchers.IO) {
            val profileResult = NeteaseApi.getUserProfile()
            profileResult.fold(
                onSuccess = { profile ->
                    if (profile.userId > 0) {
                        Result.success(profile.nickname)
                    } else {
                        Result.failure(Exception("Login failed"))
                    }
                },
                onFailure = { e ->
                    Result.failure(e)
                }
            )
        }
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
