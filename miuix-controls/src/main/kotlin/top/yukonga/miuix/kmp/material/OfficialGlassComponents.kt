// HyperMusic API adapters. Rendering and motion are owned by the upstream miuix-glass components.
package top.yukonga.miuix.kmp.material

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.glass.*
import top.yukonga.miuix.kmp.glass.backdrop.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.PopupLayout

internal val LocalOfficialGlassPopup = compositionLocalOf { false }

/** One official button configuration for toolbar actions and menu anchors. */
@Composable
internal fun OfficialGlassToolbarButton(onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, content: @Composable () -> Unit) {
    GlassIconButton(onClick = { if (enabled) onClick() }, modifier = modifier,
        backdrop = officialButtonBackdrop(), size = GlassTopAppBarDefaults.ButtonSize, content = content)
}

/** Adapts legacy popup hosting only; the official popup owns size, placement and all motion. */
@Composable
internal fun OfficialGlassListPopup(show: Boolean, onDismissRequest: (() -> Unit)?,
    onDismissFinished: (() -> Unit)?, maxHeight: Dp?, minWidth: Dp,
    entries: List<DropdownEntry>? = null, content: @Composable () -> Unit = {}) {
    val mounted = remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    Spacer(Modifier.onGloballyPositioned { coordinates ->
        coordinates.parentLayoutCoordinates?.let {
            bounds = it.boundsInWindow(); anchor.containerBounds = bounds
            anchor.cornerRadius = with(density) { (bounds.size.minDimension / 2f).toDp().coerceAtLeast(1.dp) }
        }
    })
    LaunchedEffect(show) { if (show) mounted.value = true }
    val scene = officialGlassBackdrop()
    if (entries != null) LaunchedEffect(show) {
        if (!show) { snapshotFlow { anchor.contentHidden }.first { !it }; mounted.value = false; onDismissFinished?.invoke() }
    }
    PopupLayout(visible = mounted, enterTransition = EnterTransition.None, exitTransition = ExitTransition.None,
        enableWindowDim = false, renderInRootScaffold = true, enableBackHandler = false) {
        // PopupLayout mounts lazily. Give the official transition its collapsed initial state.
        var attached by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { withFrameNanos { }; attached = true }
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalOfficialGlassPopup provides true) {
                if (entries != null) GlassIconDropdownMenu(show && attached, { onDismissRequest?.invoke() }, anchor, {}, entries, scene,
                    sizing = GlassPopupSizing(minWidth = minWidth, maxHeight = maxHeight ?: GlassPopupDefaults.MaxHeight))
                else GlassDropdownPopupContent(show, { onDismissRequest?.invoke() }, bounds, scene,
                    sizing = GlassPopupSizing(minWidth = minWidth, maxHeight = maxHeight ?: GlassPopupDefaults.MaxHeight),
                    onDismissFinished = { mounted.value = false; onDismissFinished?.invoke() }) { content() }
            }
        }
    }
}

@Composable
internal fun OfficialGlassTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit,
    modifier: Modifier, height: Dp, spacing: Dp, contour: Boolean) {
    // Inline tabs cannot sample an ancestor recording that contains themselves.
    val color = MiuixTheme.colorScheme.surface
    val base = remember(color) { object : Backdrop {
        override val isCoordinatesDependent = false
        override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Float) { drawRect(color) }
    } }
    if (contour) GlassSegmentedTabRow(tabs, selected, onSelect, base,
        modifier = modifier.fillMaxWidth(), height = height, shadow = null)
    else GlassTabRow(tabs, selected, onSelect, null, modifier = modifier.fillMaxWidth(),
        height = height, tabGap = spacing, colors = GlassTabRowDefaults.neutralColors(), shadow = null)
}

@Composable
internal fun OfficialGlassIconMenu(entries: List<DropdownEntry>, modifier: Modifier,
    enabled: Boolean, collapseOnSelection: Boolean, onExpandedChange: ((Boolean) -> Unit)?,
    maxHeight: Dp?, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val mounted = remember { mutableStateOf(false) }
    val anchor = rememberGlassPopupAnchor()
    val scene = officialGlassBackdrop()
    val currentOnExpanded by rememberUpdatedState(onExpandedChange)
    fun setExpanded(value: Boolean) {
        if (expanded != value) { expanded = value; if (value) mounted.value = true; currentOnExpanded?.invoke(value) }
    }
    LaunchedEffect(expanded) {
        if (!expanded) { snapshotFlow { anchor.contentHidden }.first { !it }; mounted.value = false }
    }
    val size = GlassTopAppBarDefaults.ButtonSize
    OfficialGlassToolbarButton(onClick = { setExpanded(!expanded) }, enabled = enabled,
        modifier = modifier.glassPopupAnchor(anchor, size / 2)) {
        // The transition copies the icon, not the entire 44dp button. Report its own bounds
        // so the copy rejoins the centred icon without resizing or shifting on the last frame.
        Box(Modifier.glassPopupAnchorContent(anchor), contentAlignment = androidx.compose.ui.Alignment.Center) { content() }
    }
    PopupLayout(visible = mounted, enterTransition = EnterTransition.None, exitTransition = ExitTransition.None,
        enableWindowDim = false, renderInRootScaffold = true, enableBackHandler = false) {
        var attached by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { withFrameNanos { }; attached = true }
        Box(Modifier.fillMaxSize()) {
            GlassIconDropdownMenu(show = expanded && attached, onDismissRequest = { setExpanded(false) }, anchor = anchor,
                anchorContent = content, entries = entries, backdrop = scene,
                collapseOnSelection = collapseOnSelection,
                sizing = GlassPopupSizing(maxHeight = maxHeight ?: GlassPopupDefaults.MaxHeight))
        }
    }
}

@Composable
internal fun OfficialGlassEntryPreference(entries: List<DropdownEntry>, title: String, modifier: Modifier,
    titleColor: BasicComponentColors, summary: String?, summaryColor: BasicComponentColors,
    startAction: (@Composable () -> Unit)?, bottomAction: (@Composable () -> Unit)?,
    insideMargin: PaddingValues, maxHeight: Dp?, enabled: Boolean, showValue: Boolean,
    collapseOnSelection: Boolean, onExpandedChange: ((Boolean) -> Unit)?) {
    val items = entries.flatMap { group -> group.items.map { if (group.enabled) it else it.copy(enabled = false) } }
    val entry = DropdownEntry(items)
    GlassDropdownPreference(onSelectedIndexChange = { index -> items.getOrNull(index)?.onClick?.invoke() },
        items = items.map { it.text }, selectedIndex = items.indexOfFirst { it.selected }, title = title,
        backdrop = officialGlassBackdrop(), modifier = modifier, enabled = enabled,
        showValue = showValue, titleColor = titleColor, summary = summary, summaryColor = summaryColor,
        startAction = startAction, bottomAction = bottomAction, insideMargin = insideMargin,
        sizing = GlassPopupSizing(maxHeight = maxHeight ?: GlassPopupDefaults.MaxHeight),
        onExpandedChange = onExpandedChange, entry = entry, collapseOnSelection = collapseOnSelection)
}
