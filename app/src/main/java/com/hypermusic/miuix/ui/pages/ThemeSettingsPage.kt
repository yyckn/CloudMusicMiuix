package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.pageNavigationIconInset
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import android.content.Intent
import android.net.Uri
import com.hypermusic.miuix.ui.navigation.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Slider
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.theme.ThemeSettings
import com.hypermusic.miuix.ui.theme.accentMenuIndex
import com.hypermusic.miuix.ui.theme.accentSavedIndex
import com.hypermusic.miuix.R
import com.hypermusic.miuix.ui.components.SettingsItemIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.automirrored.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
private fun ThemeSettingIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
    Spacer(Modifier.width(12.dp))
}

private val ThemePreviewMaxHeight = 400.dp
private val ThemePreviewLabelFontSize = 10.sp

@Composable
private fun ThemePreview(previewWidth: Dp, previewHeight: Dp) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.requiredSize(width = previewWidth, height = previewHeight)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF27272A)).padding(3.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(MiuixTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("11:45", fontSize = ThemePreviewLabelFontSize)
                Text("14%", fontSize = ThemePreviewLabelFontSize)
            }
            Text(
                "HyperMusic",
                fontSize = MiuixTheme.textStyles.title2.fontSize * 0.9f,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            )
            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("收藏的音乐", "收藏歌单", "人生必听的100首音乐").forEachIndexed { index, label ->
                    Row(
                        Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val swatch = when (index) {
                            0 -> MiuixTheme.colorScheme.primary
                            1 -> MiuixTheme.colorScheme.primaryVariant
                            else -> MiuixTheme.colorScheme.tertiaryContainer
                        }
                        Spacer(Modifier.size(28.dp).background(swatch, RoundedCornerShape(6.dp)))
                        Text(label, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icons = listOf(MiuixIcons.Home, MiuixIcons.ListView, MiuixIcons.Settings)
                listOf("推荐", "收藏", "设置").forEachIndexed { index, label ->
                    val color = if (index == 1) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(icons[index], contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
                        Text(label, fontSize = ThemePreviewLabelFontSize, color = color, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSettingsPage(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val windowSize = LocalWindowInfo.current.containerSize
    val windowAspectRatio = windowSize.width.coerceAtLeast(1).toFloat() / windowSize.height.coerceAtLeast(1)
    var showScaleDialog by remember { mutableStateOf(false) }
    var scaleInput by remember { mutableStateOf("") }
    var pendingScale by remember(ThemeSettings.pageScale) { mutableStateOf(ThemeSettings.pageScale) }
    
    val themeModeOptions = remember {
        listOf("浅色模式", "深色模式", "跟随系统")
    }
    val floatingBarAlignOptions = remember {
        listOf("居左", "居中", "居右")
    }
    val liquidGlassEffectOptions = remember {
        listOf("通透", "中等", "厚重")
    }

    BackHandler {
        onBackClick()
    }

    val blurBackdrop = rememberPageBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "主题设置",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier
                                .padding(start = pageNavigationIconInset())
                                .clickable { onBackClick() }
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = modifier.padding(top = pageViewportTopInset(paddingValues.calculateTopPadding())).fillMaxSize()
                .pageLayerBackdrop(blurBackdrop)
        ) {
            // Include the preview's outer margins when fitting it into a small window.
            val previewHeight = minOf(
                ThemePreviewMaxHeight,
                (maxHeight - pageScrollTopInset(paddingValues.calculateTopPadding()) - 32.dp).coerceAtLeast(1.dp),
                (maxWidth - 24.dp).coerceAtLeast(1.dp) / windowAspectRatio
            )
            val previewWidth = previewHeight * windowAspectRatio
            val wideLayout = maxWidth >= 600.dp && maxWidth >= previewWidth + 32.dp + 280.dp
            val previewViewportHeight = maxHeight - pageScrollTopInset(paddingValues.calculateTopPadding())
            Row(Modifier.fillMaxSize()) {
                if (wideLayout) {
                    Box(Modifier.width(previewWidth + 32.dp).fillMaxSize(fraction = 1f).padding(top = pageScrollTopInset(paddingValues.calculateTopPadding()))
                        .verticalScroll(rememberScrollState()).heightIn(min = previewViewportHeight),
                        contentAlignment = Alignment.Center) {
                        ThemePreview(previewWidth, previewHeight)
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = pageScrollTopInset(paddingValues.calculateTopPadding()), bottom = FeedBottomPads.None),
                    overscrollEffect = null
                ) {
                    if (!wideLayout) item { ThemePreview(previewWidth, previewHeight) }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    TabRowWithContour(tabs = themeModeOptions, selectedTabIndex = ThemeSettings.themeModeIndex,
                        onTabSelected = { ThemeSettings.updateThemeModeIndex(it) }, modifier = Modifier.padding(8.dp))
                    BasicComponent(title = "启用 Monet 颜色", summary = "使用系统壁纸颜色生成主题",
                        startAction = { ThemeSettingIcon(Icons.Rounded.Wallpaper) },
                        endActions = { Switch(checked = ThemeSettings.followMonet, onCheckedChange = { ThemeSettings.updateFollowMonet(it) }) },
                        onClick = { ThemeSettings.updateFollowMonet(!ThemeSettings.followMonet) })
                    if (ThemeSettings.followMonet) {
                        WindowDropdownPreference(title = "强调色", startAction = { ThemeSettingIcon(Icons.Rounded.Colorize) }, items = listOf("跟随系统", "跟随音乐来源主题色", "红色", "粉红", "紫色", "深紫", "靛蓝", "蓝色", "青色", "青绿", "绿色", "黄色", "琥珀", "橙色", "棕色", "蓝灰", "浅粉"),
                            selectedIndex = accentMenuIndex(ThemeSettings.accentIndex), onSelectedIndexChange = { ThemeSettings.updateAccentIndex(accentSavedIndex(it)) })
                        WindowDropdownPreference(title = "色彩风格", startAction = { ThemeSettingIcon(Icons.Rounded.Style) }, items = listOf("柔和（Tonal Spot）", "中性（Neutral）", "鲜艳（Vibrant）", "表现力（Expressive）", "彩虹（Rainbow）", "水果沙拉（Fruit Salad）", "单色（Monochrome）", "保真（Fidelity）", "内容（Content）"),
                            selectedIndex = ThemeSettings.paletteStyleIndex, onSelectedIndexChange = { ThemeSettings.updatePaletteStyleIndex(it) })
                        WindowDropdownPreference(title = "色彩标准", startAction = { ThemeSettingIcon(Icons.Rounded.DesignServices) }, items = listOf("SPEC_2021", "SPEC_2025"),
                            selectedIndex = ThemeSettings.colorSpecIndex, onSelectedIndexChange = { ThemeSettings.updateColorSpecIndex(it) })
                    }
                    WindowDropdownPreference(
                        title = "控件样式",
                        startAction = { ThemeSettingIcon(Icons.Rounded.WaterDrop) },
                        items = listOf("柔光玻璃", "高斯模糊"),
                        selectedIndex = ThemeSettings.controlStyleIndex,
                        onSelectedIndexChange = { ThemeSettings.updateControlStyle(it) }
                    )
                }
            }
            item { SmallTitle("底栏") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    WindowDropdownPreference(
                        title = "底栏样式",
                        startAction = { ThemeSettingIcon(Icons.Rounded.CallToAction) },
                        items = listOf("悬浮底栏（液态玻璃）", "正常底栏（高斯模糊）"),
                        selectedIndex = if (ThemeSettings.useFloatingBar) 0 else 1,
                        onSelectedIndexChange = { ThemeSettings.updateUseFloatingBar(it == 0) }
                    )
                    if (ThemeSettings.useFloatingBar) {
                        WindowDropdownPreference(
                            title = "适人握持",
                            summary = "选择底栏对齐方向，适用于阔屏和平板机型",
                            startAction = { ThemeSettingIcon(Icons.AutoMirrored.Rounded.AlignHorizontalLeft) },
                            items = floatingBarAlignOptions,
                            selectedIndex = ThemeSettings.floatingBarAlignmentIndex,
                            onSelectedIndexChange = { ThemeSettings.updateFloatingBarAlignment(it) }
                        )
                        BasicComponent(
                            title = "自动折叠底栏上方的播放器",
                            summary = "此选项仅在手机布局生效",
                            startAction = {
                                SettingsItemIcon(R.drawable.ic_md3_bottom_panel_close)
                                Spacer(Modifier.width(12.dp))
                            },
                            endActions = {
                                Switch(
                                    checked = ThemeSettings.autoCollapseFloatingPlayer,
                                    onCheckedChange = { ThemeSettings.updateAutoCollapseFloatingPlayer(it) }
                                )
                            },
                            onClick = { ThemeSettings.updateAutoCollapseFloatingPlayer(!ThemeSettings.autoCollapseFloatingPlayer) }
                        )
                        WindowDropdownPreference(
                            title = "液态玻璃效果",
                            startAction = { ThemeSettingIcon(Icons.Rounded.WaterDrop) },
                            items = liquidGlassEffectOptions,
                            selectedIndex = ThemeSettings.liquidGlassEffectIndex,
                            onSelectedIndexChange = { ThemeSettings.updateLiquidGlassEffectIndex(it) }
                        )
                    } else {
                        BasicComponent(
                            title = "高斯模糊效果",
                            startAction = { ThemeSettingIcon(Icons.Rounded.WaterDrop) },
                            endActions = {
                                Switch(
                                    checked = ThemeSettings.gaussianBlurEnabled,
                                    onCheckedChange = { ThemeSettings.updateGaussianBlur(it) }
                                )
                            },
                            onClick = { ThemeSettings.updateGaussianBlur(!ThemeSettings.gaussianBlurEnabled) }
                        )
                    }
                }
            }
            item { SmallTitle("其他") }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    WindowDropdownPreference(
                        title = "默认首页",
                        summary = "启动时默认显示的页面",
                        startAction = { ThemeSettingIcon(MiuixIcons.Home) },
                        items = listOf("推荐页", "收藏页"),
                        selectedIndex = ThemeSettings.defaultHomePageIndex,
                        onSelectedIndexChange = { ThemeSettings.updateDefaultHomePage(it) }
                    )
                    BasicComponent(title = "预测性返回手势", summary = "返回时预览页面过渡动画",
                        startAction = { ThemeSettingIcon(Icons.AutoMirrored.Rounded.MenuOpen) },
                        endActions = { Switch(checked = ThemeSettings.predictiveBackEnabled, onCheckedChange = { ThemeSettings.updatePredictiveBack(it) }) },
                        onClick = { ThemeSettings.updatePredictiveBack(!ThemeSettings.predictiveBackEnabled) })
                    ArrowPreference(title = "界面缩放",
                        startAction = { ThemeSettingIcon(Icons.Rounded.AspectRatio) },
                        endActions = { Text("${(pendingScale * 100).roundToInt()}%", color = MiuixTheme.colorScheme.onSurfaceVariantActions) },
                        onClick = {
                            scaleInput = (ThemeSettings.pageScale * 100).roundToInt().toString()
                            showScaleDialog = true
                        },
                        bottomAction = {
                            Slider(value = pendingScale,
                                onValueChange = { pendingScale = (it * 100).roundToInt() / 100f },
                                onValueChangeFinished = { ThemeSettings.updatePageScale(pendingScale) },
                                valueRange = 0.8f..1.1f, steps = 29,
                                showKeyPoints = true, keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f),
                                magnetThreshold = 0.01f, hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                modifier = Modifier.padding(vertical = 4.dp))
                        })
                }
            }
                }
            }
        }
        OverlayDialog(show = showScaleDialog, title = "界面缩放", summary = "80% - 110%",
            onDismissRequest = { showScaleDialog = false }) {
            TextField(value = scaleInput,
                onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) scaleInput = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                trailingIcon = { Text("%", modifier = Modifier.padding(horizontal = 16.dp)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(text = "取消", onClick = { showScaleDialog = false }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton(text = "确定", onClick = {
                    scaleInput.toIntOrNull()?.let { ThemeSettings.updatePageScale(it.coerceIn(80, 110) / 100f) }
                    showScaleDialog = false
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }
}
