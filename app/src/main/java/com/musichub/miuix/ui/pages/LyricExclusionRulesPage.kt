package com.musichub.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.musichub.miuix.data.lyrics.LyricExclusionRules
import com.musichub.miuix.ui.FeedBottomPads
import com.musichub.miuix.ui.components.BlurredBar
import com.musichub.miuix.ui.components.pageLayerBackdrop
import com.musichub.miuix.ui.components.rememberPageBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun LyricExclusionRulesPage(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRulesChanged: () -> Unit = {}
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberPageBlurBackdrop()

    var showDialog by remember { mutableStateOf(false) }
    var editingRuleId by remember { mutableStateOf<String?>(null) }
    var ruleInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("") }

    fun openAddDialog() {
        editingRuleId = null
        ruleInput = ""
        durationInput = ""
        showDialog = true
    }

    fun openEditDialog(rule: LyricExclusionRules.Rule) {
        editingRuleId = rule.id
        ruleInput = rule.pattern
        durationInput = LyricExclusionRules.durationInputText(rule)
        showDialog = true
    }

    BackHandler { onBackClick() }

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "歌词排除规则",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { onBackClick() }
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pageLayerBackdrop(blurBackdrop),
            contentPadding = PaddingValues(bottom = FeedBottomPads.None)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            // ---------------- 默认规则 ----------------
            item { SmallTitle("默认规则") }

            items(LyricExclusionRules.defaultRules, key = { it.id }) { rule ->
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp)
                ) {
                    BasicComponent(
                        title = rule.pattern,
                        summary = LyricExclusionRules.describe(rule).ifBlank { null },
                        endActions = {
                            Switch(
                                checked = LyricExclusionRules.isDefaultRuleEnabled(rule.id),
                                onCheckedChange = { checked ->
                                    LyricExclusionRules.updateDefaultRuleEnabled(rule.id, checked)
                                    onRulesChanged()
                                }
                            )
                        }
                    )
                }
            }

            // ---------------- 用户规则 ----------------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallTitle("用户规则", modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { openAddDialog() },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加规则",
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = USER_RULE_HELP,
                        modifier = Modifier.padding(16.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }
            }

            items(LyricExclusionRules.userRules, key = { it.id }) { rule ->
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp)
                ) {
                    BasicComponent(
                        title = rule.pattern.ifBlank { "(仅时长)" },
                        summary = LyricExclusionRules.describe(rule).ifBlank { null },
                        onClick = { openEditDialog(rule) },
                        endActions = {
                            IconButton(
                                onClick = { openEditDialog(rule) }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "编辑",
                                    tint = MiuixTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    LyricExclusionRules.removeUserRule(rule.id)
                                    onRulesChanged()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "删除",
                                    tint = MiuixTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    BasicComponent(
                        title = "添加规则",
                        summary = "支持关键词、歌曲信息、正则与自定义时长",
                        onClick = { openAddDialog() },
                        startAction = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 16.dp),
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }

        OverlayDialog(
            show = showDialog,
            title = if (editingRuleId == null) "添加规则" else "编辑规则",
            summary = "规则可填写关键词、歌曲信息模板或正则；歌词时长可选，单位毫秒",
            onDismissRequest = { showDialog = false },
            content = {
                Column {
                    TextField(
                        value = ruleInput,
                        onValueChange = { ruleInput = it },
                        label = "规则，例如 制作人： 或 {title} - {auther}",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = durationInput,
                        onValueChange = { newValue ->
                            if (newValue.matches(Regex("^\\d*-?\\d*$"))) {
                                durationInput = newValue
                            }
                        },
                        label = "歌词时长（毫秒，可留空，如 1000-2000）",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showDialog = false },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        TextButton(
                            text = "保存",
                            onClick = {
                                val pattern = ruleInput.trim()
                                val (durationMin, durationMax) = LyricExclusionRules.parseDurationRange(durationInput)
                                if (pattern.isNotEmpty() || durationMin > 0 || durationMax > 0) {
                                    val currentId = editingRuleId
                                    if (currentId == null) {
                                        LyricExclusionRules.addUserRule(
                                            LyricExclusionRules.Rule(
                                                pattern = pattern,
                                                durationMs = durationMax,
                                                durationMinMs = durationMin
                                            )
                                        )
                                    } else {
                                        LyricExclusionRules.updateUserRule(
                                            LyricExclusionRules.Rule(
                                                id = currentId,
                                                pattern = pattern,
                                                durationMs = durationMax,
                                                durationMinMs = durationMin
                                            )
                                        )
                                    }
                                    onRulesChanged()
                                }
                                showDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        )
    }
}

private val USER_RULE_HELP = """
支持以下规则类型：
1. 关键词：直接输入要排除的片段，例如 制作人：。
2. 歌曲信息：可用 {title}（歌名）、{auther}（歌手）、{album}（专辑），例如 {title} - {auther}。
   多位歌手时，歌词中的歌手部分只要包含其中任意一位即算匹配（如实际为 A/B/C，写作 ABD、AB、BCD 都算）；比较时忽略“-”两边的空格以及括号内容。
3. 正则：例如 ^(作词|作曲)\s*[:：]；以 ^ 或 $ 开头结尾、或包含 .*、|、[]、\d 等会被识别为正则。
4. 仅时长：规则留空，只填写歌词时长（毫秒，支持 1000-2000 这类区间），将排除时长落在该范围内的行。
""".trimIndent()
