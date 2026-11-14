package com.hypermusic.miuix.ui.pages

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.hypermusic.miuix.data.audio.*
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.scan.QrScannerActivity
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun SoundEffectsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scroll = MiuixScrollBehavior()
    val backdrop = rememberPageBlurBackdrop()
    var showSaveMenu by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf<UserSoundPreset?>(null) }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringExtra(QrScannerActivity.EXTRA_RESULT)
        if (result.resultCode == Activity.RESULT_OK && text != null) {
            runCatching { SoundPresetLink.decode(text) }.onSuccess {
                AudioSettings.saveNew(it.name, it.effects)
                Toast.makeText(context, "音效已导入", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "音效二维码无效", Toast.LENGTH_LONG).show() }
        }
    }
    BackHandler(enabled = showNameDialog) { }
    com.hypermusic.miuix.ui.navigation.BackHandler(enabled = !showNameDialog) { onBack() }
    Scaffold(topBar = {
        BlurredBar(backdrop = backdrop) {
            SmallTopAppBar(title = "音效", color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                scrollBehavior = scroll, defaultWindowInsetsPadding = false,
                navigationIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.padding(start = 8.dp).clickable { onBack() }) },
                actions = {
                    if (AudioSettings.modified) {
                        Box {
                            IconButton(onClick = {
                                if (AudioSettings.selectedEffects == null) {
                                    presetName = ""; showNameDialog = true
                                } else showSaveMenu = true
                            }) { Icon(Icons.Filled.Save, "保存音效") }
                            OverlayListPopup(show = showSaveMenu, onDismissRequest = { showSaveMenu = false }) {
                                ListPopupColumn {
                                    BasicComponent(title = "保存为新预设", onClick = { showSaveMenu = false; presetName = ""; showNameDialog = true })
                                    BasicComponent(title = "覆盖当前", onClick = { AudioSettings.overwriteSelected(); showSaveMenu = false })
                                }
                            }
                        }
                    }
                })
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).overScrollVertical()
            .nestedScroll(scroll.nestedScrollConnection).pageLayerBackdrop(backdrop),
            contentPadding = PaddingValues(bottom = FeedBottomPads.None), overscrollEffect = null) {
            item { SmallTitle("预设音效") }
            items(SoundPresets.all.size) { index ->
                val preset = SoundPresets.all[index]
                SoundPresetOption(preset.name, preset.id == AudioSettings.selectedId && !AudioSettings.modified,
                    onSelect = { AudioSettings.select(preset.id) })
            }
            item { SmallTitle("用户音效") }
            items(AudioSettings.userPresets.size, key = { AudioSettings.userPresets[it].id }) { index ->
                val preset = AudioSettings.userPresets[index]
                SoundPresetOption(preset.name, preset.id == AudioSettings.selectedId && !AudioSettings.modified,
                    onSelect = { AudioSettings.select(preset.id) }, onShare = { sharing = preset },
                    onDelete = { AudioSettings.remove(preset.id) })
            }
            item {
                Button(onClick = {
                    scanner.launch(Intent(context, QrScannerActivity::class.java).putExtra(QrScannerActivity.EXTRA_KIND, QrScannerActivity.KIND_SOUND))
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp),
                    colors = ButtonDefaults.buttonColorsPrimary()) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("扫码导入音效")
                }
            }
            item { SmallTitle("均衡器") }
            item { EqualizerEditor(AudioSettings.current) }
            item { SmallTitle("高级设置") }
            item {
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    val effects = AudioSettings.current
                    EffectSlider("低频截止", effects.highpass, 20f, 2000f, 20f, logarithmic = true) { AudioSettings.set(effects.copy(highpass = it.roundToInt().toFloat())) }
                    EffectSlider("高频截止", effects.lowpass, 500f, 20000f, 20000f, logarithmic = true) { AudioSettings.set(effects.copy(lowpass = it.roundToInt().toFloat())) }
                    EffectSlider("饱和失真", effects.drive, 0f, 1f, 0f) { AudioSettings.set(effects.copy(drive = it)) }
                    EffectSlider("比特降质", effects.crush, 0f, 1f, 0f) { AudioSettings.set(effects.copy(crush = it)) }
                    EffectSlider("抖晃", effects.wow, 0f, 1f, 0f) { AudioSettings.set(effects.copy(wow = it)) }
                    EffectSlider("黑胶噪声", effects.noise, 0f, 1f, 0f) { AudioSettings.set(effects.copy(noise = it)) }
                    EffectSlider("立体声宽度", effects.width, 0f, 2f, 1f) { AudioSettings.set(effects.copy(width = it)) }
                    EffectSlider("空间混响", effects.space, 0f, 1f, 0f) { AudioSettings.set(effects.copy(space = it)) }
                    EffectSlider("动态压缩", effects.punch, 0f, 1f, 0f) { AudioSettings.set(effects.copy(punch = it)) }
                }
            }
        }
        OverlayDialog(show = showNameDialog, title = "保存为新预设", onDismissRequest = {}) {
            TextField(value = presetName, onValueChange = { if (it.length <= 80) presetName = it }, label = "音效名称", singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "取消", onClick = { showNameDialog = false }, modifier = Modifier.weight(1f))
                TextButton(text = "保存", enabled = presetName.isNotBlank() && presetName.none(Char::isISOControl), onClick = {
                    AudioSettings.saveNew(presetName); showNameDialog = false
                }, colors = ButtonDefaults.textButtonColorsPrimary(), modifier = Modifier.weight(1f))
            }
        }
        SoundPresetShareSheet(sharing, onDismiss = { sharing = null })
    }
}

@Composable
private fun SoundPresetOption(name: String, selected: Boolean, onSelect: () -> Unit, onShare: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp)) {
        BasicComponent(onClick = onSelect, endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) Icon(MiuixIcons.Ok, "已选择", tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                if (onShare != null) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(MiuixIcons.More, "更多") }
                        OverlayListPopup(show = menu, onDismissRequest = { menu = false }) {
                            ListPopupColumn {
                                BasicComponent(title = "分享", onClick = { menu = false; onShare() })
                                if (onDelete != null) BasicComponent(title = "删除", onClick = { menu = false; onDelete() })
                            }
                        }
                    }
                }
            }
        }) {
            Text(name, fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun EqualizerEditor(effects: SoundEffects) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            val columnsWidth = maxOf(520.dp, maxWidth - 60.dp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.width(60.dp).padding(end = 4.dp), horizontalAlignment = Alignment.End) {
                    Text("Hz", style = MiuixTheme.textStyles.footnote1)
                    Box(Modifier.fillMaxWidth().height(176.dp)) {
                        Text("+12dB", Modifier.align(Alignment.TopEnd), style = MiuixTheme.textStyles.footnote1)
                        Text("-12dB", Modifier.align(Alignment.BottomEnd), style = MiuixTheme.textStyles.footnote1)
                    }
                    Text("dB", style = MiuixTheme.textStyles.footnote1)
                    Spacer(Modifier.height(32.dp))
                }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    Row(Modifier.width(columnsWidth), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SoundPresets.frequencies.forEachIndexed { index, hz ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (hz >= 1000) "${hz / 1000}k" else "$hz", style = MiuixTheme.textStyles.footnote1)
                                VerticalSlider(value = effects.bands[index], onValueChange = { v ->
                                    val bands = AudioSettings.current.bands.toMutableList(); bands[index] = (v * 10).roundToInt() / 10f
                                    AudioSettings.set(AudioSettings.current.copy(bands = bands))
                                }, valueRange = -12f..12f, steps = 239, showKeyPoints = true, keyPoints = listOf(0f), magnetThreshold = .002f,
                                    modifier = Modifier.height(176.dp).width(40.dp))
                                Text("${if (effects.bands[index] > 0) "+" else ""}${"%.1f".format(java.util.Locale.ROOT, effects.bands[index])}", style = MiuixTheme.textStyles.footnote1)
                                IconButton(onClick = {
                                    val current = AudioSettings.current
                                    val bands = current.bands.toMutableList(); bands[index] = 0f
                                    AudioSettings.set(current.copy(bands = bands))
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Filled.Refresh, "重置 ${hz} Hz", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectSlider(title: String, value: Float, min: Float, max: Float, neutral: Float, logarithmic: Boolean = false, onChange: (Float) -> Unit) {
    fun position(v: Float): Float = if (logarithmic) ((ln(v) - ln(min)) / (ln(max) - ln(min))) else (v - min) / (max - min)
    fun actual(p: Float): Float = if (logarithmic) exp(ln(min) + p * (ln(max) - ln(min))) else min + p * (max - min)
    val display = if (logarithmic) "${value.roundToInt()} Hz" else "${(value * 100).roundToInt()}%"
    BasicComponent(title = title, endActions = { Text(display, style = MiuixTheme.textStyles.body2) }, bottomAction = {
        Slider(value = position(value).coerceIn(0f, 1f), onValueChange = { p ->
            val next = actual(p).coerceIn(min, max)
            onChange(if (logarithmic) next else (next * 100).roundToInt() / 100f)
        }, valueRange = 0f..1f, steps = if (logarithmic) 999 else ((max - min) * 100).roundToInt() - 1,
            showKeyPoints = true, keyPoints = listOf(position(neutral)), magnetThreshold = .001f,
            modifier = Modifier.padding(vertical = 4.dp))
    })
}

@Composable
private fun SoundPresetShareSheet(preset: UserSoundPreset?, onDismiss: () -> Unit) {
    val image = remember(preset) {
        preset?.let { runCatching {
            val matrix = QRCodeWriter().encode(SoundPresetLink.encode(it.name, it.effects), BarcodeFormat.QR_CODE, 768, 768,
                mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 4))
            val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height) }.asImageBitmap()
        }.getOrNull() }
    }
    OverlayBottomSheet(show = preset != null, title = "分享音效", insideMargin = DpSize(12.dp, 0.dp), onDismissRequest = onDismiss,
        endAction = { IconButton(onClick = onDismiss) { Icon(MiuixIcons.Close, "关闭") } }) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (image != null) Image(image, "音效二维码", Modifier.sizeIn(maxWidth = 256.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.White))
            else Text("无法生成音效二维码", color = MiuixTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text("使用 HyperMusic 扫描此二维码导入音效预设。", style = MiuixTheme.textStyles.footnote1, textAlign = TextAlign.Center)
        }
    }
}
