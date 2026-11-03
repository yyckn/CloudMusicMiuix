package com.hypermusic.miuix.ui.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Open a system-owned output selector; unsupported implementations fall back to Bluetooth settings. */
object MediaOutputLauncher {
    enum class Result { XIAOMI, SAMSUNG, SYSTEM, LEGACY_PANEL, BLUETOOTH_SETTINGS, UNAVAILABLE }

    fun open(context: Context): Result {
        val brand = Build.MANUFACTURER.lowercase()
        if (brand in setOf("xiaomi", "redmi", "poco") && startSystemActivity(context,
                Intent("miui.intent.action.ACTIVITY_MIPLAY_DETAIL"))) return Result.XIAOMI
        if (brand == "samsung" && startSystemActivity(context,
                Intent().setClassName("com.samsung.android.mdx.quickboard", "com.samsung.android.mdx.quickboard.view.MediaActivity"))) return Result.SAMSUNG

        val systemIntent = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
            .setPackage("com.android.systemui").putExtra("package_name", context.packageName)
        val receiver = context.packageManager.queryBroadcastReceivers(systemIntent, 0).firstOrNull {
            val info = it.activityInfo
            info != null && info.exported && isSystemApp(info.applicationInfo) &&
                (info.permission == null || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
        }
        if (receiver != null && runCatching { context.sendBroadcast(systemIntent) }.isSuccess) return Result.SYSTEM

        val panel = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
            .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
        if (startSystemActivity(context, panel)) return Result.LEGACY_PANEL
        if (startSystemActivity(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) return Result.BLUETOOTH_SETTINGS
        return Result.UNAVAILABLE
    }

    private fun isSystemApp(info: ApplicationInfo?): Boolean = info != null &&
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun startSystemActivity(context: Context, intent: Intent): Boolean {
        val info = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).firstOrNull {
            val activity = it.activityInfo
            activity != null && activity.exported && isSystemApp(activity.applicationInfo) &&
                (activity.permission == null || context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED)
        }?.activityInfo ?: return false
        return runCatching {
            context.startActivity(intent.setClassName(info.packageName, info.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
