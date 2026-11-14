package com.hypermusic.miuix.ui.pages

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.data.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 新平台共用原登录页的标题栏和二维码布局。 */
@Composable
internal fun AdditionalPlatformLoginPage(platform: MusicPlatform, onBack: () -> Unit, onSuccess: () -> Unit) {
    require(platform in setOf(MusicPlatform.BODIAN, MusicPlatform.KUGOU, MusicPlatform.QISHUI))
    val context = LocalContext.current
    val bodian = remember { val client = BodianClient(context); BodianAuth(context, client) }
    val kugou = remember { val client = KugouClient(context); KugouAuth(context, client) }
    val qishui = remember { val client = QishuiClient(context); QishuiAuth(context, client) }
    var revision by remember(platform) { mutableIntStateOf(0) }
    var bitmap by remember(platform) { mutableStateOf<Bitmap?>(null) }
    var status by remember(platform) { mutableStateOf("正在获取二维码…") }
    var deadline by remember(platform) { mutableLongStateOf(0L) }
    var secondsRemaining by remember(platform) { mutableIntStateOf(0) }
    LaunchedEffect(deadline) {
        while (deadline > 0L) {
            secondsRemaining = ((deadline - android.os.SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0).toInt()
            if (secondsRemaining == 0) break
            delay(1000)
        }
    }
    val success by rememberUpdatedState(onSuccess)
    LaunchedEffect(platform, revision) {
        bitmap = null
        deadline = 0L
        secondsRemaining = 0
        status = "正在获取二维码…"
        var b: BodianAuth.Qr? = null
        var k: KugouAuth.Qr? = null
        var q: QishuiAuth.Qr? = null
        try {
            when (platform) {
                MusicPlatform.BODIAN -> { b = bodian.createQr(); bitmap = generateQrCode(b.url); status = "请使用波点音乐扫码登录" }
                MusicPlatform.KUGOU -> { k = kugou.createQr(); bitmap = generateQrCode(k.url); status = "请使用酷狗音乐扫码登录" }
                MusicPlatform.QISHUI -> { q = qishui.createQr(); bitmap = BitmapFactory.decodeByteArray(q.image, 0, q.image.size); status = "请使用抖音扫码登录汽水音乐" }
                else -> error("不支持的登录平台")
            }
            check(bitmap != null) { "无法生成二维码" }
            deadline = b?.deadline ?: k?.deadline ?: q?.expiresAt ?: 0L
            secondsRemaining = ((deadline - android.os.SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0).toInt()
            while (true) {
                delay(2000)
                val state = when (platform) {
                    MusicPlatform.BODIAN -> when (bodian.check(requireNotNull(b))) {
                        BodianAuth.State.WAITING -> 0; BodianAuth.State.SCANNED -> 1; BodianAuth.State.EXPIRED -> 2; BodianAuth.State.CONFIRMED -> 3
                    }
                    MusicPlatform.KUGOU -> when (kugou.check(requireNotNull(k))) {
                        KugouAuth.State.WAITING -> 0; KugouAuth.State.SCANNED -> 1; KugouAuth.State.EXPIRED -> 2; KugouAuth.State.CONFIRMED -> 3
                    }
                    MusicPlatform.QISHUI -> when (qishui.check(requireNotNull(q))) {
                        QishuiAuth.State.WAITING -> 0; QishuiAuth.State.SCANNED -> 1; QishuiAuth.State.EXPIRED, QishuiAuth.State.REFUSED -> 2; QishuiAuth.State.CONFIRMED -> 3
                    }
                    else -> 2
                }
                when (state) {
                    1 -> status = "请在手机上确认登录"
                    2 -> { deadline = 0L; secondsRemaining = 0; status = "二维码已过期或授权已取消，请刷新"; break }
                    3 -> { status = "登录成功"; success(); break }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = "登录失败：${e.message ?: "未知错误"}" }
        finally { b?.let(bodian::cancel); k?.let(kugou::cancel); q?.let(qishui::cancel) }
    }
    LoginPageFrame(onBackClick = onBack, onRefreshClick = { revision++ }) {
        val configuration = LocalConfiguration.current
        if (configuration.screenWidthDp > configuration.screenHeightDp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCodeBox(bitmap, qqBackground = true)
                    AdditionalQrCountdown(bitmap, secondsRemaining)
                }
                Column(Modifier.weight(1f).padding(start = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AdditionalLoginStatus(status, bitmap, context)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                QrCodeBox(bitmap, qqBackground = true)
                AdditionalQrCountdown(bitmap, secondsRemaining)
                Spacer(Modifier.height(24.dp))
                AdditionalLoginStatus(status, bitmap, context)
            }
        }
    }
}

@Composable
private fun AdditionalQrCountdown(bitmap: Bitmap?, secondsRemaining: Int) {
    if (bitmap == null) return
    Spacer(Modifier.height(12.dp))
    Text(if (secondsRemaining > 0) "二维码有效期剩余${secondsRemaining}秒" else "二维码已过期",
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}

@Composable
private fun AdditionalLoginStatus(status: String, bitmap: Bitmap?, context: android.content.Context) {
    Text(status, textAlign = TextAlign.Center, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    Spacer(Modifier.height(16.dp))
    SaveLoginQrButton(bitmap, context)
}
