package com.hypermusic.miuix.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 应用图标切换：通过启用/禁用 launcher activity-alias 实现。
 * 默认使用音符图标，其余图标为可选项。
 */
object AppIconSettings {

    enum class AppIcon { NOTES, NETEASE, KUGOU, KUGOU_CLASSIC, NETEASE_CLASSIC, QISHUI_CLASSIC, QQ_CLASSIC }
    enum class AppName(val label: String, val suffix: String) {
        HYPERMUSIC("HyperMusic", ""), MUSIC("Music", "NameMusic"), CHINESE("音乐", "NameChinese"),
        NETEASE("网易云音乐", "NameNetease"), QQ("QQ音乐", "NameQq")
    }

    private const val PREFS_NAME = "app_icon_settings"
    private const val KEY_ICON = "current_icon"
    private const val NOTES_ALIAS = ".IconNotes"
    private const val NETEASE_ALIAS = ".IconNetease"
    private const val KUGOU_MONET_ALIAS = ".IconKugouMonet"
    private const val KUGOU_ALIAS = ".IconKugou"
    private const val NETEASE_CLASSIC_ALIAS = ".IconNeteaseClassic"

    var current by mutableStateOf(AppIcon.NOTES)
        private set
    var currentName by mutableStateOf(AppName.HYPERMUSIC)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        current = runCatching {
            val name = prefs?.getString(KEY_ICON, AppIcon.NOTES.name) ?: AppIcon.NOTES.name
            AppIcon.valueOf(name)
        }.getOrDefault(AppIcon.NOTES)
        currentName = runCatching { AppName.valueOf(prefs?.getString("current_name", null) ?: AppName.HYPERMUSIC.name) }
            .getOrDefault(AppName.HYPERMUSIC)
    }

    fun selectName(context: Context, name: AppName) {
        if (name == currentName) return
        currentName = name
        prefs?.edit()?.putString("current_name", name.name)?.apply()
        apply(context, current)
    }

    fun select(context: Context, icon: AppIcon) {
        current = icon
        prefs?.edit()?.putString(KEY_ICON, icon.name)?.apply()
        apply(context, icon)
    }

    fun applySaved(context: Context) {
        apply(context, current)
    }

    private fun apply(context: Context, icon: AppIcon) {
        val packageName = context.packageName
        val pm = context.packageManager
        val aliases = mapOf(
            AppIcon.NOTES to NOTES_ALIAS,
            AppIcon.NETEASE to NETEASE_ALIAS,
            AppIcon.KUGOU to KUGOU_MONET_ALIAS,
            AppIcon.KUGOU_CLASSIC to KUGOU_ALIAS,
            AppIcon.NETEASE_CLASSIC to NETEASE_CLASSIC_ALIAS,
            AppIcon.QISHUI_CLASSIC to ".IconQishuiClassic",
            AppIcon.QQ_CLASSIC to ".IconQqClassic"
        )
        val changes = aliases.flatMap { (appIcon, alias) -> AppName.entries.mapNotNull { name ->
            val component = ComponentName(packageName, com.hypermusic.miuix.MainActivity::class.java.name.substringBeforeLast('.') + alias + name.suffix)
            val desired = if (appIcon == icon && name == currentName) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (pm.getComponentEnabledSetting(component) != desired) {
                PackageManager.ComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP)
            } else null
        } }
        if (changes.isNotEmpty()) pm.setComponentEnabledSettings(changes)
    }
}
