// Android adapter for the Compose version used by HyperMusic.
package top.yukonga.miuix.kmp.glass

import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class LayerOutsets(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp) {
    companion object { val Zero = LayerOutsets(0.dp, 0.dp, 0.dp, 0.dp) }
}

/** Compose versions before LayerOutsets retain shadow pixels using modulated layer alpha. */
internal var GraphicsLayerScope.outsets: LayerOutsets
    get() = LayerOutsets.Zero
    set(value) { if (value != LayerOutsets.Zero) compositingStrategy = CompositingStrategy.ModulateAlpha }
