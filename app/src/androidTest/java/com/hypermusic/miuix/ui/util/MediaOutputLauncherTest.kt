package com.hypermusic.miuix.ui.util

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaOutputLauncherTest {
    @Test fun xiaomiOutputActivityCanBeOpened() {
        assumeTrue(Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(MediaOutputLauncher.Result.XIAOMI, MediaOutputLauncher.open(context))
    }
}
