package com.hypermusic.miuix

import android.content.Context
import com.hypermusic.miuix.data.api.BodianAuth
import com.hypermusic.miuix.data.api.BodianClient
import com.hypermusic.miuix.data.api.KugouAuth
import com.hypermusic.miuix.data.api.KugouClient
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.PlatformManager
import com.hypermusic.miuix.data.api.QishuiAuth
import com.hypermusic.miuix.data.api.QishuiClient
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.data.audio.AudioSettings
import com.hypermusic.miuix.data.audio.separation.VocalSeparationEngine
import com.hypermusic.miuix.data.history.ListeningHistory
import com.hypermusic.miuix.data.lyrics.BodianLyricsApi
import com.hypermusic.miuix.data.lyrics.KugouLyricsApi
import com.hypermusic.miuix.data.lyrics.LyricExclusionRules
import com.hypermusic.miuix.data.lyrics.LyricMatches
import com.hypermusic.miuix.data.lyrics.QishuiLyricsApi
import com.hypermusic.miuix.ui.theme.AgreementSettings
import com.hypermusic.miuix.ui.theme.AppIconSettings
import com.hypermusic.miuix.ui.theme.KeyboardShortcutSettings
import com.hypermusic.miuix.ui.theme.OnboardingSettings
import com.hypermusic.miuix.ui.theme.ThemeSettings

/** Application composition root. Keep subsystem initialization out of the UI entry point. */
internal object AppBootstrap {
    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            NeteaseApi.init(appContext)
            ListeningHistory.init(appContext)
            ListeningHistory.retryAtLaunch()
            QqMusicApi.init(appContext)
            PlatformManager.init(appContext)
            ThemeSettings.init(appContext)
            AudioSettings.init(appContext)
            VocalSeparationEngine.init(appContext)
            LyricMatches.init(appContext)
            BodianLyricsApi.init(appContext)
            KugouLyricsApi.init(appContext)
            QishuiLyricsApi.init(appContext)
            AppIconSettings.init(appContext)
            AppIconSettings.applySaved(appContext)
            LyricExclusionRules.init(appContext)
            KeyboardShortcutSettings.init(appContext)
            AgreementSettings.init(appContext)
            OnboardingSettings.init(appContext)
            recoverMissingAccountIfNeeded(appContext)
            initialized = true
        }
    }

    private fun recoverMissingAccountIfNeeded(appContext: Context) {
        if (!OnboardingSettings.completed || !AgreementSettings.accepted) return
        val loggedIn = when (PlatformManager.current) {
            MusicPlatform.QQ -> QqMusicApi.isLoggedIn()
            MusicPlatform.NETEASE -> NeteaseApi.isLoggedIn()
            MusicPlatform.BODIAN -> {
                val api = BodianClient(appContext)
                BodianAuth(appContext, api).currentAccount() != null
            }
            MusicPlatform.KUGOU -> {
                val api = KugouClient(appContext)
                KugouAuth(appContext, api).account() != null
            }
            MusicPlatform.QISHUI -> runCatching {
                val api = QishuiClient(appContext)
                QishuiAuth(appContext, api).account() != null
            }.getOrDefault(false)
        }
        OnboardingSettings.recoverMissingAccount(AgreementSettings.accepted, loggedIn)
    }
}
