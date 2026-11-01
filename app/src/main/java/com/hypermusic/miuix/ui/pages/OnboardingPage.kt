package com.hypermusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import android.graphics.RuntimeShader
import android.view.RoundedCorner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import com.hypermusic.miuix.R
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.data.api.PlatformManager
import com.hypermusic.miuix.ui.theme.AgreementSettings
import com.hypermusic.miuix.ui.theme.OnboardingSettings
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OnboardingPage() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val screenCorners = remember(view, view.rootWindowInsets, density) {
        val insets = view.rootWindowInsets
        listOf(RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
            RoundedCorner.POSITION_BOTTOM_RIGHT, RoundedCorner.POSITION_BOTTOM_LEFT)
            .map { insets?.getRoundedCorner(it)?.radius?.toFloat() ?: with(density) { 28.dp.toPx() } }
    }
    val screenCorner = screenCorners.maxOrNull() ?: 0f
    val step = OnboardingSettings.step
    var agreed by rememberSaveable { mutableStateOf(false) }
    var startButtonBounds by remember { mutableStateOf(Rect.Zero) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    var roundWelcomeTransition by remember { mutableStateOf(false) }
    val width = pageSize.width.toFloat().coerceAtLeast(1f)
    val height = pageSize.height.toFloat().coerceAtLeast(1f)
    val buttonSize = with(density) { 70.dp.toPx() }
    val buttonBounds = if (startButtonBounds == Rect.Zero)
        Rect((width - buttonSize) / 2f, height * 0.9f - buttonSize / 2f,
            (width + buttonSize) / 2f, height * 0.9f + buttonSize / 2f)
    else startButtonBounds
    val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun back() {
        if (step > 0) {
            roundWelcomeTransition = step == 1
            OnboardingSettings.goTo(if (step == 4) 2 else step - 1)
        }
    }
    BackHandler { back() }

    AnimatedContent(targetState = step, modifier = Modifier.fillMaxSize().onSizeChanged { pageSize = it }, label = "onboarding_step", transitionSpec = {
        if (initialState == 0 && targetState == 1) {
            fadeIn(tween(1)) togetherWith
                (scaleOut(tween(650, easing = easing), targetScale = 0.9f) + fadeOut(tween(650)))
        } else if (initialState == 1 && targetState == 0) {
            ((scaleIn(tween(650, easing = easing), initialScale = 0.9f) + fadeIn(tween(650))) togetherWith
                fadeOut(tween(650))).apply { targetContentZIndex = -1f }
        } else if (targetState > initialState) {
            slideInHorizontally(tween(400, easing = easing)) { it } togetherWith
                slideOutHorizontally(tween(400, easing = easing)) { -it }
        } else {
            slideInHorizontally(tween(400, easing = easing)) { -it } togetherWith
                slideOutHorizontally(tween(400, easing = easing)) { it }
        }
    }) { page ->
    val expandProgress by transition.animateFloat(transitionSpec = { tween(650, easing = easing) }, label = "onboarding_expand") { state ->
        if (state == EnterExitState.Visible) 1f else 0f
    }
    val isWelcomeTransition = roundWelcomeTransition && transition.currentState != transition.targetState
    Box(Modifier.fillMaxSize().graphicsLayer {
        if (page == 1 && isWelcomeTransition) {
            val progress = expandProgress
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = buttonBounds.width / width + (1f - buttonBounds.width / width) * progress
            scaleY = buttonBounds.height / height + (1f - buttonBounds.height / height) * progress
            translationX = buttonBounds.left * (1f - progress)
            translationY = buttonBounds.top * (1f - progress)
            val radius = screenCorner * progress + minOf(width, height) * 0.5f * (1f - progress)
            shape = RoundedCornerShape(radius.toDp())
            clip = true
        } else if (page == 0 && isWelcomeTransition) {
            shape = AbsoluteRoundedCornerShape(
                topLeft = screenCorners[0].toDp(), topRight = screenCorners[1].toDp(),
                bottomRight = screenCorners[2].toDp(), bottomLeft = screenCorners[3].toDp()
            )
            clip = true
        }
    }) {
    when (page) {
        0 -> OnboardingWelcome(completed = false, onClick = { roundWelcomeTransition = true; OnboardingSettings.goTo(1) },
            onButtonBounds = { if (transition.currentState == transition.targetState) startButtonBounds = it })
        1 -> OnboardingScaffold(onBack = ::back,
            buttonLabel = "同意并继续", buttonEnabled = agreed,
            onContinue = { roundWelcomeTransition = false; AgreementSettings.accept(); OnboardingSettings.goTo(2) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                item { OnboardingSectionHeader(R.drawable.oobe_hyperceiler_terms, "协议与声明") }
                item {
                    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(AGREEMENT_TITLE, style = MiuixTheme.textStyles.title2)
                            Spacer(Modifier.height(12.dp))
                            Text(AGREEMENT_META, style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Spacer(Modifier.height(16.dp))
                            Text(AGREEMENT_WARNING, style = MiuixTheme.textStyles.body2)
                            AGREEMENT_SECTIONS.forEach { section ->
                                Spacer(Modifier.height(20.dp))
                                Text(section.title, style = MiuixTheme.textStyles.title3)
                                Spacer(Modifier.height(8.dp))
                                Text(section.body, style = MiuixTheme.textStyles.body2)
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                        Spacer(Modifier.height(12.dp))
                        Card(Modifier.fillMaxWidth()) {
                            CheckboxPreference(title = "我已阅读并同意用户协议与免责声明", checked = agreed,
                                onCheckedChange = { agreed = it })
                        }
                    }
                }
            }
        }
        2 -> OnboardingScaffold(onBack = ::back,
            buttonLabel = "继续", onContinue = {
                val loggedIn = if (PlatformManager.current == MusicPlatform.QQ) QqMusicApi.isLoggedIn() else NeteaseApi.isLoggedIn()
                OnboardingSettings.goTo(if (loggedIn) 4 else 3)
            }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                OnboardingSectionHeader(R.drawable.oobe_hyperceiler_settings, "音乐平台")
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Text("选择你要使用的音乐平台", modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Card(Modifier.fillMaxWidth()) {
                        RadioButtonPreference(title = "网易云音乐", selected = PlatformManager.current == MusicPlatform.NETEASE,
                            onClick = { PlatformManager.select(MusicPlatform.NETEASE) })
                        RadioButtonPreference(title = "QQ音乐", selected = PlatformManager.current == MusicPlatform.QQ,
                            onClick = { PlatformManager.select(MusicPlatform.QQ) })
                    }
                    Text("*稍后也可在APP内切换平台。", modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
        3 -> if (PlatformManager.current == MusicPlatform.QQ) {
            QqLoginPage(onBack = ::back, onSuccess = { OnboardingSettings.goTo(4) })
        } else {
            QrLoginPage(onBackClick = ::back, onLoginSuccess = {
                NeteaseApi.saveCurrentAccount()
                OnboardingSettings.goTo(4)
            }, context = context)
        }
        else -> OnboardingWelcome(completed = true, onClick = { OnboardingSettings.finish() }, onBack = ::back)
    }
    }
    }
}

@Composable
private fun OnboardingWelcome(completed: Boolean, onClick: () -> Unit, onBack: () -> Unit = {}, onButtonBounds: (Rect) -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
    OnboardingGlow()
    Scaffold(containerColor = Color.Transparent, topBar = {
        if (completed) SmallTopAppBar(title = "", color = Color.Transparent, navigationIcon = {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                tint = Color.White, modifier = Modifier.padding(start = 8.dp).clickable { onBack() })
        })
    }, bottomBar = {
        Surface(color = Color.Transparent) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(top = 20.dp, bottom = if (completed) 20.dp else 40.dp),
                contentAlignment = Alignment.Center) {
                if (completed) {
                    Button(onClick = onClick, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary()) { Text("开始使用") }
                } else {
                    Button(onClick = onClick, modifier = Modifier.size(70.dp).onGloballyPositioned { onButtonBounds(it.boundsInRoot()) }, cornerRadius = 35.dp,
                        minWidth = 0.dp, minHeight = 0.dp, insideMargin = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(color = Color.White.copy(alpha = 0.25f), contentColor = Color.White.copy(alpha = 0.86f))) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "开始设置",
                            modifier = Modifier.size(28.dp), tint = Color.White.copy(alpha = 0.86f))
                    }
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (completed) {
                Image(painter = painterResource(R.drawable.ic_music_note), contentDescription = null,
                    modifier = Modifier.size(100.dp), colorFilter = ColorFilter.tint(Color.White))
            } else {
                Box(Modifier.size(120.dp).background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(30.dp)),
                    contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(R.drawable.ic_music_note), contentDescription = null,
                        modifier = Modifier.size(80.dp), colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.86f)))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(if (completed) "设置完毕" else "HyperMusic", fontSize = 35.sp,
                fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = if (completed) 1f else 0.86f))
            if (completed) {
                Spacer(Modifier.height(12.dp))
                Text("一切准备就绪", color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
    }
}

@Composable
private fun OnboardingSectionHeader(icon: Int, title: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(100.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MiuixTheme.textStyles.title1, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OnboardingGlow() {
    val motion = rememberInfiniteTransition(label = "onboarding_glow")
    val phase by motion.animateFloat(initialValue = 0f, targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart), label = "glow_motion")
    val shader = remember { RuntimeShader(ONBOARDING_GLOW_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(Modifier.fillMaxSize()) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("phase", phase)
        drawRect(brush)
    }
}

// An animated, continuously blended color field, rendered on the GPU.
private const val ONBOARDING_GLOW_SHADER = """
    uniform float2 resolution;
    uniform float phase;

    half4 main(float2 position) {
        float2 uv = position / resolution;
        float2 p = (position - resolution * 0.5) / min(resolution.x, resolution.y);
        float a = sin(p.x * 2.5 + p.y * 1.8 + sin(phase) * 1.5);
        float b = cos(p.y * 3.0 - p.x * 1.4 + cos(phase) * 1.6);
        float c = sin((p.x + p.y) * 2.0 + sin(phase + 1.4) * 1.7);
        float field = clamp(0.5 + 0.19 * a + 0.18 * b + 0.13 * c, 0.0, 1.0);
        float3 red = float3(0.961, 0.157, 0.157);
        float3 violet = float3(0.604, 0.659, 0.961);
        float3 blue = float3(0.302, 0.290, 0.843);
        float3 color = mix(red, violet, smoothstep(0.08, 0.57, field));
        color = mix(color, blue, smoothstep(0.43, 0.95, field));
        float light = 0.62 + 0.25 * sin(p.x * 1.6 - p.y * 2.1 + cos(phase + 0.7) * 1.5);
        color *= clamp(light, 0.35, 0.9);
        return half4(half3(color), 1.0);
    }
"""

@Composable
private fun OnboardingScaffold(
    onBack: () -> Unit,
    buttonLabel: String,
    buttonEnabled: Boolean = true,
    onContinue: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(topBar = {
        SmallTopAppBar(title = "", navigationIcon = {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                modifier = Modifier.padding(start = 8.dp).clickable { onBack() })
        })
    }, bottomBar = {
        Surface(color = MiuixTheme.colorScheme.surface) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Button(onClick = onContinue, enabled = buttonEnabled, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary()) { Text(buttonLabel) }
                }
            }
        }
    }, content = content)
}
