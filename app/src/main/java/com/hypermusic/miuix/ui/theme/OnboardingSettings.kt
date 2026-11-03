package com.hypermusic.miuix.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** First-run setup state, separate from account credentials and agreement acceptance. */
object OnboardingSettings {
    private var prefs: SharedPreferences? = null
    var completed by mutableStateOf(false)
        private set
    var step by mutableStateOf(0)
        private set

    fun init(context: Context) {
        val store = context.applicationContext.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        prefs = store
        completed = store.getBoolean("completed", false)
        step = store.getInt("step", 0).coerceIn(0, 4)
        if (!AgreementSettings.accepted && step > 1) step = 1
        if (!completed) store.edit().putBoolean("started", true).putInt("step", step).commit()
    }

    fun goTo(value: Int) {
        step = value.coerceIn(0, 4)
        prefs?.edit()?.putBoolean("started", true)?.putInt("step", step)?.apply()
    }

    fun finish() {
        prefs?.edit()?.putBoolean("completed", true)?.remove("step")?.commit()
        completed = true
    }

    fun restart() {
        step = 0
        prefs?.edit()?.putBoolean("started", true)?.putInt("step", 0)
            ?.putBoolean("completed", false)?.commit()
        completed = false
    }
}
