package com.musichub.miuix.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 用户协议同意状态。未同意时应用启动即展示协议页，且无法返回退出。 */
object AgreementSettings {
    private const val PREFS_NAME = "agreement_settings"
    private const val KEY_ACCEPTED = "accepted"

    private var prefs: SharedPreferences? = null

    var accepted by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        accepted = prefs?.getBoolean(KEY_ACCEPTED, false) ?: false
    }

    fun accept() {
        accepted = true
        prefs?.edit()?.putBoolean(KEY_ACCEPTED, true)?.apply()
    }
}
