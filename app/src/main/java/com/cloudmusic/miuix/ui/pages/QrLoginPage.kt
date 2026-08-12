package com.cloudmusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudmusic.miuix.data.api.NeteaseApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
    var qrKey by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusText by remember { mutableStateOf("正在获取二维码...") }
    var statusCode by remember { mutableIntStateOf(0) }

    BackHandler {
        onBackClick()
    }

    // 获取二维码
    LaunchedEffect(Unit) {
        NeteaseApi.getLoginQrKey().fold(
            onSuccess = { key ->
                qrKey = key
                // 生成二维码
                qrBitmap = generateQrCode(NeteaseApi.getQrCodeUrl(key))
                statusText = "请使用网易云音乐APP扫描二维码"
                statusCode = 801

                // 轮询检查登录状态
                var loginSuccess = false
                while (!loginSuccess) {
                    delay(2000)
                    NeteaseApi.checkQrLoginStatus(key).fold(
                        onSuccess = { code ->
                            android.util.Log.d("QrLoginPage", "Login status code: $code")
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
                                    // 获取用户信息
                                    NeteaseApi.getUserInfo().fold(
                                        onSuccess = { nickname ->
                                            onLoginSuccess(nickname)
                                        },
                                        onFailure = {
                                            onLoginSuccess("")
                                        }
                                    )
                                }
                            }
                        },
                        onFailure = { e ->
                            statusText = "检查登录状态失败: ${e.message}"
                            android.util.Log.e("QrLoginPage", "Check login status failed", e)
                        }
                    )
                }
            },
            onFailure = { e ->
                statusText = "获取二维码失败: ${e.message}"
                android.util.Log.e("QrLoginPage", "Get QR key failed", e)
            }
        )
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "扫码登录",
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
                .padding(top = paddingValues.calculateTopPadding())
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "网易云音乐扫码登录",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 二维码
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

            Spacer(modifier = Modifier.height(24.dp))

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

            // 保存二维码到相册按钮
            Button(
                onClick = {
                    qrBitmap?.let { bitmap ->
                        val saved = saveBitmapToGallery(context, bitmap, "qrcode_${System.currentTimeMillis()}")
                        if (saved) {
                            Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = MiuixIcons.Download,
                    contentDescription = "Download"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "保存二维码到相册")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 刷新登录状态按钮
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            if (statusCode == 800 || statusCode == 8821) {
                                // 二维码过期，重新获取
                                statusCode = 0
                                statusText = "正在获取二维码..."
                                NeteaseApi.getLoginQrKey().fold(
                                    onSuccess = { key ->
                                        qrKey = key
                                        qrBitmap = generateQrCode(NeteaseApi.getQrCodeUrl(key))
                                        statusText = "请使用网易云音乐APP扫描二维码"
                                        statusCode = 801
                                    },
                                    onFailure = { e ->
                                        statusText = "获取二维码失败: ${e.message}"
                                    }
                                )
                            } else if (qrKey.isNotEmpty()) {
                                // 手动刷新登录状态
                                statusText = "正在检查登录状态..."
                                val result = NeteaseApi.checkQrLoginStatus(qrKey)
                                result.fold(
                                    onSuccess = { code ->
                                        statusCode = code
                                        statusText = when (code) {
                                            800, 8821 -> "二维码已过期，请刷新"
                                            801 -> "请使用网易云音乐APP扫描二维码"
                                            802 -> "请在手机上确认登录"
                                            803 -> {
                                                // 登录成功，获取用户信息
                                                NeteaseApi.getUserInfo().fold(
                                                    onSuccess = { nickname -> onLoginSuccess(nickname) },
                                                    onFailure = { onLoginSuccess("") }
                                                )
                                                "登录成功"
                                            }
                                            else -> "未知状态: $code"
                                        }
                                    },
                                    onFailure = { e ->
                                        statusText = "检查登录状态失败: ${e.message}"
                                    }
                                )
                            } else {
                                statusText = "请先获取二维码"
                            }
                        } catch (e: Exception) {
                            statusText = "操作失败: ${e.message}"
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

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
    return try {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            true
        } ?: false
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
