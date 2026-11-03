package com.hypermusic.miuix.ui.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.data.api.UserCredentialLink
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ShareCredentialQrSheet(show: Boolean, platform: String, onDismiss: () -> Unit) {
    val bitmapResult = remember(show, platform) {
        if (!show) null else runCatching {
            val credential = if (platform == "qq") QqMusicApi.exportCredential() ?: error("账号已退出") else {
                val allowed = setOf("MUSIC_A_T", "MUSIC_R_T", "MUSIC_SNS", "MUSIC_U", "NMTID", "__csrf")
                com.google.gson.JsonObject().apply {
                    NeteaseApi.getCookie().split(';').forEach { raw ->
                        val parts = raw.trim().split('=', limit = 2)
                        if (parts.size == 2 && parts[0] in allowed) addProperty(parts[0], parts[1])
                    }
                }.toString()
            }
            val link = UserCredentialLink.encode(platform, credential)
            val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 1024, 1024,
                mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4))
            val pixels = IntArray(matrix.width * matrix.height) { index ->
                if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
            Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
            }.asImageBitmap()
        }
    }
    OverlayBottomSheet(show = show, title = "分享用户凭证", insideMargin = DpSize(12.dp, 0.dp),
        onDismissRequest = onDismiss, endAction = {
            IconButton(onClick = onDismiss) { Icon(MiuixIcons.Close, contentDescription = "关闭") }
        }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val image = bitmapResult?.getOrNull()
            if (image != null) {
                Image(bitmap = image, contentDescription = "用户凭证二维码", modifier = Modifier
                    .sizeIn(maxWidth = 256.dp, maxHeight = 256.dp).fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp)).background(Color.White))
                Spacer(Modifier.height(16.dp))
                Text("请在另一设备上打开此APP，然后点击“登录新账号”，点击“扫码用户数据二维码”。支持使用相机等系统级应用扫描此二维码。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            } else Text("无法生成二维码，请检查账号凭证是否有效。", color = MiuixTheme.colorScheme.error)
        }
    }
}
