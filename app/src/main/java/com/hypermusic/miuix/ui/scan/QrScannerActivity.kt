package com.hypermusic.miuix.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.hypermusic.miuix.data.api.UserCredentialLink
import com.hypermusic.miuix.ui.theme.CloudMusicMiuixTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Image
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScannerActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    private val handled = AtomicBoolean(false)
    private var camera: Camera? = null
    private var torch by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private lateinit var previewView: PreviewView
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else error = "未获得相机权限，可从相册选择二维码"
    }
    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::scanImage)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        val detector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val active = camera ?: return false
                val zoom = active.cameraInfo.zoomState.value ?: return false
                active.cameraControl.setZoomRatio((zoom.zoomRatio * detector.scaleFactor).coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio))
                return true
            }
        })
        previewView.setOnTouchListener { _, event -> detector.onTouchEvent(event); true }
        setContent { CloudMusicMiuixTheme {
            Scaffold(topBar = {
                SmallTopAppBar(title = "扫描二维码", navigationIcon = {
                    IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                }, actions = {
                    IconButton(onClick = { gallery.launch("image/*") }) {
                        Icon(MiuixIcons.Image, contentDescription = "相册")
                    }
                    IconButton(onClick = {
                        camera?.let { torch = !torch; it.cameraControl.enableTorch(torch) }
                    }) {
                        Icon(if (torch) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                            contentDescription = if (torch) "关闭闪光灯" else "打开闪光灯")
                    }
                })
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) {
                        val side = minOf(size.width, size.height) * 0.7f
                        val left = (size.width - side) / 2f
                        val top = (size.height - side) / 2f
                        val radius = 20.dp.toPx()
                        val cutout = Path().apply { addRoundRect(RoundRect(left, top, left + side, top + side, CornerRadius(radius))) }
                        clipPath(cutout, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.5f)) }
                        drawRoundRect(Color.White, topLeft = Offset(left, top), size = Size(side, side),
                            cornerRadius = CornerRadius(radius), style = Stroke(width = 2.dp.toPx()))
                    }
                    error?.let { Text(it, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp), color = Color.White) }
                }
            }
        } }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    @ExperimentalGetImage
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { frame ->
                    val image = frame.image
                    if (image == null) frame.close() else scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes -> codes.firstOrNull()?.rawValue?.let { result ->
                            if (runCatching { UserCredentialLink.decode(result) }.isSuccess) finishWithResult(result)
                            else error = "请扫描 HyperMusic 用户数据二维码"
                        } }.addOnCompleteListener { frame.close() }
                }
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis).also { camera = it }
            }.onFailure { error = "无法打开相机，可从相册选择二维码" }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun scanImage(uri: Uri) {
        val image = runCatching { InputImage.fromFilePath(this, uri) }.getOrElse { error = "无法读取图片"; return }
        scanner.process(image).addOnSuccessListener { codes ->
            val value = codes.firstOrNull()?.rawValue
            if (value != null && runCatching { UserCredentialLink.decode(value) }.isSuccess) finishWithResult(value)
            else Toast.makeText(this, "未找到有效的用户数据二维码", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { Toast.makeText(this, "无法识别二维码", Toast.LENGTH_SHORT).show() }
    }
    private fun finishWithResult(value: String) {
        if (!handled.compareAndSet(false, true)) return
        runOnUiThread { setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, value)); finish() }
    }
    override fun onDestroy() { super.onDestroy(); scanner.close(); executor.shutdown() }
    companion object { const val EXTRA_RESULT = "result" }
}
