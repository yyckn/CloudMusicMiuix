// HyperMusic soft-glass integration. Optical material: HaoZai000/softglass (Apache-2.0).
package top.yukonga.miuix.kmp.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.material.softglass.*
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 全局材质开关和各控件共用的采样源，不与业务层或账号状态耦合。 */
val LocalSoftGlassEnabled = compositionLocalOf { false }
val LocalSoftGlassTabsEnabled = compositionLocalOf { true }
internal val LocalGlassDark = compositionLocalOf { false }
internal val LocalGlassToolbar = compositionLocalOf { false }
internal val LocalGlassNavigation = compositionLocalOf { false }
private val LocalGlassScene = compositionLocalOf<GlassScene?> { null }

/** 只采样 Scaffold 正文，标题栏和弹层不能进入自己的采样源。 */
@Stable
private class GlassScene : Backdrop {
    var contentOffset by mutableFloatStateOf(0f)
    var layer: LayerBackdrop? = null
    var coordinates: LayoutCoordinates? = null
    private val inverse = InverseLayerScope()
    override val isCoordinatesDependent = true
    override val offsetResidualX get() = layer?.offsetResidualX ?: 0f
    override val offsetResidualY get() = layer?.offsetResidualY ?: 0f
    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Int) {
        drawScene(density, coordinates, layerBlock, downscaleFactor.toFloat())
    }
    fun DrawScope.drawScene(density: Density, coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Float) {
        val source = layer ?: return
        val origin = this@GlassScene.coordinates?.takeIf { it.isAttached } ?: return
        val consumer = coordinates?.takeIf { it.isAttached } ?: return
        // Window 弹层与页面属于不同窗口，必须使用屏幕坐标，不能比较各自的窗口内坐标。
        val offset = consumer.positionOnScreen() - origin.positionOnScreen()
        val consumerSize = (density as? BackdropEffectScope)?.size ?: size
        withTransform({
            if (layerBlock != null) with(inverse.apply { reset() }) { inverseTransform(density, consumerSize, layerBlock) }
            val factor = downscaleFactor.coerceAtLeast(1f)
            translate(-offset.x / factor, -offset.y / factor)
            scale(1f / factor, 1f / factor, Offset.Zero)
        }) { drawLayer(source.graphicsLayer) }
    }
}

private class OfficialBackdrop(private val scene: GlassScene) : top.yukonga.miuix.kmp.glass.backdrop.Backdrop {
    override val isCoordinatesDependent = true
    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Float) {
        with(scene) { drawScene(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Composable
internal fun officialGlassBackdrop(): top.yukonga.miuix.kmp.glass.backdrop.Backdrop? {
    val scene = LocalGlassScene.current
    return remember(scene) { scene?.let(::OfficialBackdrop) }
}

@Composable
internal fun officialButtonBackdrop(): top.yukonga.miuix.kmp.glass.backdrop.Backdrop? =
    if (LocalGlassInBody.current) null else officialGlassBackdrop()

/** Material appears only once page content overlaps its bar. Popup buttons outside a bar float. */
@Composable
private fun glassBarOverlapFraction(): Float {
    val bar = LocalGlassBarState.current ?: return 1f
    val density = androidx.compose.ui.platform.LocalDensity.current
    val state = bar.behavior?.state
    val overlap = if (state == null) {
        (-(LocalGlassScene.current?.contentOffset ?: 0f) / with(density) { 24.dp.toPx() }).coerceIn(0f, 1f)
    } else if (state.heightOffsetLimit == 0f) {
        (-state.contentOffset / with(density) { 24.dp.toPx() }).coerceIn(0f, 1f)
    } else state.overlappedFraction
    return overlap
}

@Composable
private fun OfficialToolbarControls(content: @Composable () -> Unit) {
    if (!LocalSoftGlassEnabled.current) { content(); return }
    val overlap = glassBarOverlapFraction()
    val floating = overlap > 0f
    val progress = androidx.compose.animation.core.animateFloatAsState(if (floating) 1f else 0f,
        top.yukonga.miuix.kmp.glass.GlassMotion.topBarButtonFloat(), label = "officialToolbarMaterial")
    val ramp = rememberUpdatedState(overlap)
    val backdrop = officialGlassBackdrop()
    val defaults = top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
    val size = defaults.ButtonSize
    CompositionLocalProvider(top.yukonga.miuix.kmp.glass.LocalGlassTopAppBarContext provides
        top.yukonga.miuix.kmp.glass.GlassTopAppBarContext(
            backdrop = backdrop, buttonBackdrop = backdrop, material = defaults.buttonMaterial(),
            underlayMaterial = null, buttonUnderlayMaterial = null, floating = floating, alpha = 1f,
            ramp = ramp, materialProgress = progress, keepMaterial = floating || progress.value > 0f,
            style = GlassDefaults.Style, size = size, shape = GlassShape(size / 2), fill = defaults.buttonFill(),
            stroke = defaults.buttonStroke(), shadow = GlassShadows.Regular)) { content() }
}

@Composable
fun SoftGlassTheme(enabled: Boolean, isDark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSoftGlassEnabled provides enabled, LocalGlassDark provides isDark) {
        content()
    }
}

private val LocalGlassInBody = compositionLocalOf { false }
private class GlassBarState { var behavior by mutableStateOf<ScrollBehavior?>(null) }
private val LocalGlassBarState = compositionLocalOf<GlassBarState?> { null }

@Composable
internal fun GlassTopBarScrollSource(behavior: ScrollBehavior?) {
    val state = LocalGlassBarState.current
    SideEffect { state?.behavior = behavior }
}

@Composable
internal fun GlassScaffold(content: @Composable (Modifier) -> Unit) {
    val enabled = LocalSoftGlassEnabled.current
    val scene = remember { GlassScene() }
    val scrollObserver = remember(scene) { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            scene.contentOffset = (scene.contentOffset + consumed.y).coerceAtMost(0f)
            return Offset.Zero
        }
    } }
    val surface = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        // Scaffold 的底色在正文外；必须一起录制，不能把卡片间隙当成透明黑。
        drawRect(surface)
        drawContent()
    }
    SideEffect { scene.layer = backdrop }
    CompositionLocalProvider(LocalGlassScene provides if (enabled) scene else null,
        LocalGlassInBody provides false) {
        content(if (enabled) Modifier.nestedScroll(scrollObserver).onGloballyPositioned { scene.coordinates = it }.layerBackdrop(backdrop) else Modifier)
    }
}

@Composable
internal fun GlassBodyContent(modifier: Modifier, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassInBody provides true) { Box(modifier) { content() } }
}


@Composable
internal fun GlassToolbarContent(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassToolbar provides true, content = content)
}

/** 同时兼容应用现有的 Icon.clickable 和 IconButton 返回按钮；空导航槽不显示玻璃圆点。 */
@Composable
internal fun GlassNavigationContent(content: @Composable () -> Unit) {
    if (!LocalSoftGlassEnabled.current) { content(); return }
    CompositionLocalProvider(LocalGlassNavigation provides true) {
        val backdrop = officialButtonBackdrop()
        SubcomposeLayout { constraints ->
            val children = subcompose("navigationContent", content).map {
                it.measure(constraints.copy(minWidth = 0, minHeight = 0))
            }
            val width = children.maxOfOrNull { it.width } ?: 0
            val height = children.maxOfOrNull { it.height } ?: 0
            if (width == 0 || height == 0) layout(0, 0) {}
            else {
                val w = constraints.constrainWidth(maxOf(width, 44.dp.roundToPx()))
                val h = constraints.constrainHeight(maxOf(height, 44.dp.roundToPx()))
                val surface = subcompose("navigationSurface") {
                    top.yukonga.miuix.kmp.glass.GlassIconButton(onClick = {}, backdrop = backdrop, size = 44.dp) {}
                }.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
                layout(w, h) {
                    surface.forEach { it.placeRelative((w - it.width) / 2, (h - it.height) / 2) }
                    children.forEach { it.placeRelative((w - it.width) / 2, (h - it.height) / 2) }
                }
            }
        }
    }
}

/** 图标菜单的触发按钮也使用同一材质；普通列表内的非菜单图标按钮保持原有行为。 */
@Composable
internal fun GlassMenuIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    holdDownState: Boolean = false,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = top.yukonga.miuix.kmp.basic.IconButtonDefaults.CornerRadius,
    minHeight: Dp = top.yukonga.miuix.kmp.basic.IconButtonDefaults.MinHeight,
    minWidth: Dp = top.yukonga.miuix.kmp.basic.IconButtonDefaults.MinWidth,
    content: @Composable () -> Unit,
) {
    GlassToolbarContent {
        top.yukonga.miuix.kmp.basic.IconButton(onClick, modifier, enabled, holdDownState,
            backgroundColor, cornerRadius, minHeight, minWidth, content)
    }
}

internal enum class GlassSurface { Control, Panel, Dialog, Tab }

@Composable
internal fun Modifier.glassSurface(
    fallbackColor: Color,
    radius: Dp,
    role: GlassSurface = GlassSurface.Control,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(radius),
): Modifier {
    val scene = LocalGlassScene.current
    if (!enabled || scene == null) return squircleSurface(color = fallbackColor, cornerRadius = radius)
    val dark = LocalGlassDark.current
    val isPanel = role == GlassSurface.Panel || role == GlassSurface.Dialog
    // 新版 Miuix 的 PopupViewGlass / DialogGlass：60dp 连续模糊及三层调色，面板不做折射。
    // 按钮的强折射若直接用于大面板，会把背后的卡片边缘折成明显的分块。
    val panelColors = remember(dark) { BlurColors(blendColors = if (dark) listOf(
        BlendColorEntry(Color(0x1A000000), BlurBlendMode.PlusDarker),
        BlendColorEntry(Color(0x996C6C6C), BlurBlendMode.Luminosity),
        BlendColorEntry(Color(0x99060606), BlurBlendMode.SoftLight),
    ) else listOf(
        BlendColorEntry(Color(0x05000000), BlurBlendMode.PlusDarker),
        BlendColorEntry(Color(0x99FFFFFF), BlurBlendMode.SoftLight),
        BlendColorEntry(Color(0x99FFFFFF), BlurBlendMode.HardLight),
    )) }
    val style = remember(dark, role) {
        SoftGlassStyle(fill = if (role == GlassSurface.Dialog || role == GlassSurface.Tab) BarFill else ThemeFill,
            tone = if (role == GlassSurface.Panel || role == GlassSurface.Dialog) BrightLargeTone else BrightTone)
            .filled(dark)
    }
    val tint = if (isPanel) Color.Transparent else if (role == GlassSurface.Tab) fallbackColor.copy(alpha = .28f)
        else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = if (dark) .10f else .08f)
    val blurDp = if (role == GlassSurface.Control || role == GlassSurface.Tab) 6.dp else 12.dp
    // 正文内的 Tab 和按钮不能反向采样包含自身的图层，使用主题底色作为光学基底。
    val baseColor = MiuixTheme.colorScheme.surfaceContainer
    val base = remember(baseColor) { object : Backdrop {
        override val isCoordinatesDependent = false
        override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Int) { drawRect(baseColor) }
    } }
    val source = if (LocalGlassInBody.current && (role == GlassSurface.Control || role == GlassSurface.Tab)) base else scene
    return drawBackdrop(backdrop = source, shape = { shape }, highlight = { null }, effects = {
        if (isPanel) {
            blur(60.dp.toPx())
            blendColors(panelColors)
        } else {
            blur(blurDp.toPx())
            padding = 0f
            val scale = downscaleFactor.coerceAtLeast(1).toFloat()
            val scaled = style.copy(edgeWidth = style.edgeWidth / scale, thickness = style.thickness / scale,
                reflectOffset = style.reflectOffset / scale)
            runtimeShaderEffect("HyperMusicSoftGlass", SOFT_GLASS_LIVE_SHADER, "content") {
                applySoftGlass(scaled, size.width / scale, size.height / scale, radius.toPx() / scale)
            }
        }
    }, onDrawSurface = {
        drawRect(tint)
        if (isPanel) drawOutline(shape.createOutline(size, layoutDirection, this),
            Color.White.copy(alpha = if (dark) .16f else .30f), style = Stroke(.75.dp.toPx()))
    })
}

@Composable
internal fun Modifier.glassTabBackground(color: Color, radius: Dp, squareFallback: Boolean = false): Modifier =
    if (LocalSoftGlassEnabled.current && LocalSoftGlassTabsEnabled.current) {
        glassSurface(color, radius, GlassSurface.Tab)
    } else if (squareFallback) background(color) else squircleBackground(color = color, cornerRadius = radius)

/** Nexio 的两趟渐进模糊：顶部最大，向底部递减；叠加少量主题底色保证文字可读。 */
@Composable
internal fun Modifier.glassTopBar(color: Color): Modifier =
    background(if (LocalSoftGlassEnabled.current) Color.Transparent else color)

/** 单独的背景层向栏下延伸，不拉伸标题或增加栏的布局高度。 */
@Composable
internal fun GlassTopBarHost(content: @Composable () -> Unit) {
    val state = remember { GlassBarState() }
    CompositionLocalProvider(LocalGlassBarState provides state) {
    Box {
        if (LocalSoftGlassEnabled.current) Box(Modifier.matchParentSize().layout { measurable, constraints ->
            val extension = if (constraints.maxHeight > 0 && state.behavior != null) 48.dp.roundToPx() else 0
            val height = constraints.maxHeight + extension
            val layer = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
            layout(constraints.maxWidth, constraints.maxHeight) { layer.place(0, 0) }
        }.graphicsLayer {
            alpha = state.behavior?.state?.contentOffset?.let { (-it / 48.dp.toPx()).coerceIn(0f, 1f) } ?: 1f
        }.glassTopBarBackground())
        OfficialToolbarControls(content)
    }
    }
}

@Composable
private fun Modifier.glassTopBarBackground(): Modifier {
    val scene = LocalGlassScene.current ?: return this
    val tint = MiuixTheme.colorScheme.surface
    return drawBackdrop(backdrop = scene, shape = { RectangleShape }, highlight = { null }, effects = {
        padding = 0f
        val scale = downscaleFactor.coerceAtLeast(1).toFloat()
        val width = size.width / scale
        val height = size.height / scale
        val radius = 80.dp.toPx() / scale
        runtimeShaderEffect("HyperMusicProgressiveBar", PROGRESSIVE_BLUR_SHADER, "content") {
            setFloatUniform("contentOrigin", 0f, 0f)
            setFloatUniform("contentSize", width, height)
            setFloatUniform("maxRadius", radius)
            setFloatUniform("edgeFadeStart", .88f)
            setColorUniform("tint", tint)
            setFloatUniform("tintIntensity", .16f)
        }
        runtimeShaderEffect("HyperMusicProgressiveDenoise", PROGRESSIVE_DENOISE_SHADER, "content") {
            setFloatUniform("contentOrigin", 0f, 0f)
            setFloatUniform("contentSize", width, height)
            setFloatUniform("maxRadius", radius)
        }
    })
}
