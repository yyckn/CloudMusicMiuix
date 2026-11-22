package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.PageBackButton
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import com.hypermusic.miuix.ui.navigation.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.saveable.rememberSaveable
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.R
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.theme.AppIconSettings
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun AppIconPage(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberPageBlurBackdrop()
    val context = LocalContext.current
    val originalIcon = remember { AppIconSettings.current }
    val originalName = remember { AppIconSettings.currentName }
    var current by rememberSaveable { mutableStateOf(originalIcon) }
    var currentName by rememberSaveable { mutableStateOf(originalName) }
    var deferred by rememberSaveable { mutableStateOf(false) }
    var pendingChange by rememberSaveable { mutableStateOf<String?>(null) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }
    val dirty = current != originalIcon || currentName != originalName
    val requestBack: () -> Unit = {
        if (dirty) showLeaveDialog = true else onBackClick()
    }
    val saveAndRestart: () -> Unit = {
        runCatching {
            AppIconSettings.save(context, current, currentName)
            context.startActivity(android.content.Intent(context, com.hypermusic.miuix.AppRestartActivity::class.java)
                .putExtra("previous_pid", android.os.Process.myPid())
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            android.widget.Toast.makeText(context, it.message ?: "保存失败，请重试", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val requestIconChange: (AppIconSettings.AppIcon) -> Unit = { newIcon ->
        if (current != newIcon) {
            current = newIcon
            if (!deferred) pendingChange = "图标"
        }
    }
    BackHandler {
        if (pendingChange == null && !showSaveDialog && !showLeaveDialog) requestBack()
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "应用图标和名称",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                    actions = {
                        if (deferred && dirty) IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Filled.Save, contentDescription = "保存图标和名称")
                        }
                    },
                    navigationIcon = { PageBackButton(onClick = { requestBack() }) }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = pageViewportTopInset(paddingValues.calculateTopPadding()))
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pageLayerBackdrop(blurBackdrop),
            contentPadding = PaddingValues(top = pageScrollTopInset(paddingValues.calculateTopPadding()), bottom = FeedBottomPads.None)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item { SmallTitle("当前图标") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIconPreview(
                        icon = current,
                        size = 96.dp,
                        corner = 22.dp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(currentName.label, style = MiuixTheme.textStyles.body2)
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    val names = AppIconSettings.AppName.entries
                    OverlayDropdownPreference(title = "应用名称", items = names.map { it.label },
                        selectedIndex = names.indexOf(currentName),
                        onSelectedIndexChange = { index ->
                            val name = names[index]
                            if (name != currentName) {
                                currentName = name
                                if (!deferred) pendingChange = "名称"
                            }
                        })
                }
            }

            item { SmallTitle("莫奈取色系列") }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        IconChoice(
                            icon = AppIconSettings.AppIcon.NOTES,
                            label = "音符",
                            selected = current == AppIconSettings.AppIcon.NOTES,
                            onClick = {
                                requestIconChange(AppIconSettings.AppIcon.NOTES)
                            }
                        )
                    }
                    item {
                        IconChoice(
                            icon = AppIconSettings.AppIcon.NETEASE,
                            label = "网易云",
                            selected = current == AppIconSettings.AppIcon.NETEASE,
                            onClick = {
                                requestIconChange(AppIconSettings.AppIcon.NETEASE)
                            }
                        )
                    }
                    item {
                        IconChoice(
                            icon = AppIconSettings.AppIcon.KUGOU,
                            label = "酷狗",
                            selected = current == AppIconSettings.AppIcon.KUGOU,
                            onClick = {
                                requestIconChange(AppIconSettings.AppIcon.KUGOU)
                            }
                        )
                    }
                }
            }

            item { SmallTitle("经典图标系列") }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        IconChoice(
                            icon = AppIconSettings.AppIcon.KUGOU_CLASSIC,
                            label = "酷狗音乐",
                            selected = current == AppIconSettings.AppIcon.KUGOU_CLASSIC,
                            onClick = {
                                requestIconChange(AppIconSettings.AppIcon.KUGOU_CLASSIC)
                            }
                        )
                    }
                    item {
                        IconChoice(
                            icon = AppIconSettings.AppIcon.NETEASE_CLASSIC,
                            label = "网易云音乐",
                            selected = current == AppIconSettings.AppIcon.NETEASE_CLASSIC,
                            onClick = {
                                requestIconChange(AppIconSettings.AppIcon.NETEASE_CLASSIC)
                            }
                        )
                    }
                    item {
                        IconChoice(icon = AppIconSettings.AppIcon.QISHUI_CLASSIC, label = "汽水音乐",
                            selected = current == AppIconSettings.AppIcon.QISHUI_CLASSIC,
                            onClick = { requestIconChange(AppIconSettings.AppIcon.QISHUI_CLASSIC) })
                    }
                    item {
                        IconChoice(icon = AppIconSettings.AppIcon.QQ_CLASSIC, label = "QQ音乐",
                            selected = current == AppIconSettings.AppIcon.QQ_CLASSIC,
                            onClick = { requestIconChange(AppIconSettings.AppIcon.QQ_CLASSIC) })
                    }
                    item {
                        IconChoice(icon = AppIconSettings.AppIcon.BODIAN_CLASSIC, label = "波点音乐",
                            selected = current == AppIconSettings.AppIcon.BODIAN_CLASSIC,
                            onClick = { requestIconChange(AppIconSettings.AppIcon.BODIAN_CLASSIC) })
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        OverlayDialog(show = pendingChange != null, title = "切换${pendingChange.orEmpty()}",
            summary = "切换图标或名称需要重启APP后生效，是否继续", onDismissRequest = {}) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(text = "稍后保存并重启", onClick = { deferred = true; pendingChange = null }, modifier = Modifier.fillMaxWidth())
                TextButton(text = "继续", onClick = saveAndRestart, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
        OverlayDialog(show = showSaveDialog, title = "保存新图标或名称",
            summary = "切换图标或名称需要重启APP后生效，是否继续", onDismissRequest = {}) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "取消", onClick = { showSaveDialog = false }, modifier = Modifier.weight(1f))
                TextButton(text = "继续", onClick = saveAndRestart, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
        OverlayDialog(show = showLeaveDialog, title = "是否保存新的图标或名称",
            summary = "您修改的图标或名称还未保存，是否保存此次更改？（注意：保存需要重启APP后生效）", onDismissRequest = {}) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "废弃更改", onClick = { showLeaveDialog = false; onBackClick() }, modifier = Modifier.weight(1f))
                TextButton(text = "保存", onClick = saveAndRestart, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }
}

@Composable
private fun IconChoice(
    icon: AppIconSettings.AppIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(24.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            AppIconPreview(
                icon = icon,
                size = if (selected) 60.dp else 64.dp,
                corner = 16.dp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun AppIconPreview(
    icon: AppIconSettings.AppIcon,
    size: Dp,
    corner: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
    ) {
        if (icon in setOf(AppIconSettings.AppIcon.QISHUI_CLASSIC, AppIconSettings.AppIcon.QQ_CLASSIC, AppIconSettings.AppIcon.BODIAN_CLASSIC)) {
            Image(painter = painterResource(when (icon) {
                AppIconSettings.AppIcon.QQ_CLASSIC -> R.drawable.ic_qqmusic_classic
                AppIconSettings.AppIcon.BODIAN_CLASSIC -> R.drawable.ic_bodian_classic
                else -> R.drawable.ic_qishui_classic
            }),
                contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
        when (icon) {
            AppIconSettings.AppIcon.NOTES,
            AppIconSettings.AppIcon.NETEASE,
            AppIconSettings.AppIcon.KUGOU -> {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_background),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            AppIconSettings.AppIcon.KUGOU_CLASSIC -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0062FF))
                )
            }
            AppIconSettings.AppIcon.QISHUI_CLASSIC, AppIconSettings.AppIcon.QQ_CLASSIC, AppIconSettings.AppIcon.BODIAN_CLASSIC -> Unit
            AppIconSettings.AppIcon.NETEASE_CLASSIC -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFE60026))
                )
            }
        }

        val foreground = when (icon) {
            AppIconSettings.AppIcon.NOTES -> R.drawable.ic_music_note
            AppIconSettings.AppIcon.NETEASE,
            AppIconSettings.AppIcon.NETEASE_CLASSIC -> R.drawable.ic_launcher_foreground
            AppIconSettings.AppIcon.KUGOU,
            AppIconSettings.AppIcon.KUGOU_CLASSIC -> R.drawable.ic_launcher_kugou_foreground
            AppIconSettings.AppIcon.QISHUI_CLASSIC -> R.drawable.ic_qishui_classic
            AppIconSettings.AppIcon.QQ_CLASSIC -> R.drawable.ic_qqmusic_classic
            AppIconSettings.AppIcon.BODIAN_CLASSIC -> R.drawable.ic_bodian_classic
        }
        val foregroundFraction = if (icon == AppIconSettings.AppIcon.NOTES) 0.66f else 1.5f
        Image(
            painter = painterResource(foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(foregroundFraction)
                .align(Alignment.Center),
            contentScale = ContentScale.Fit
        )
        }
    }
}
