package com.hypermusic.miuix

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.ui.theme.OnboardingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Run only against a disposable application ID: this intentionally resets account preferences. */
@RunWith(AndroidJUnit4::class)
class StartupWithoutAccountTest {
    @Test fun recoveryPersistsPlatformChoiceAcrossRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue("Requires a disposable test application ID", context.packageName.endsWith(".oobecheck"))
        context.getSharedPreferences("agreement_settings", 0).edit().putBoolean("accepted", true).commit()
        com.hypermusic.miuix.ui.theme.AgreementSettings.init(context)
        for (platform in listOf("netease", "qq", "bodian", "kugou", "qishui")) {
            context.getSharedPreferences("music_platform", 0).edit().putString("current", platform).commit()
            context.getSharedPreferences("onboarding", 0).edit().putBoolean("completed", true).commit()
            OnboardingSettings.init(context)
            OnboardingSettings.recoverMissingAccount(agreementAccepted = true, loggedIn = false)
            assertFalse(OnboardingSettings.completed)
            assertEquals(2, OnboardingSettings.step)
            OnboardingSettings.init(context)
            assertFalse(OnboardingSettings.completed)
            assertEquals(2, OnboardingSettings.step)
        }
    }

    @Test fun completedSetupWithoutCurrentPlatformAccountReturnsToPlatformChoice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue("Requires a disposable test application ID", context.packageName.endsWith(".oobecheck"))
        for (platform in listOf("netease", "qq", "bodian", "kugou", "qishui")) {
            context.getSharedPreferences("agreement_settings", 0).edit().putBoolean("accepted", true).commit()
            context.getSharedPreferences("onboarding", 0).edit().putBoolean("completed", true).commit()
            context.getSharedPreferences("music_platform", 0).edit().putString("current", platform).commit()
            context.getSharedPreferences("netease_prefs", 0).edit().clear().commit()
            context.getSharedPreferences("qqmusic_prefs", 0).edit().clear().commit()
            context.getSharedPreferences("bodian_prefs", 0).edit().clear().commit()
            context.getSharedPreferences("kugou_prefs", 0).edit().clear().commit()
            context.getSharedPreferences("qishui_secure_session", 0).edit().clear().commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    assertFalse(OnboardingSettings.completed)
                    assertEquals(2, OnboardingSettings.step)
                }
            }
        }
    }
}
