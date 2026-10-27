package dev.plume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeWatchTest {
    private val amazon = "com.amazon.tv.launcher"
    private val amazonHome = "com.amazon.tv.launcher.ui.HomeActivity_vNext"

    @Test fun amazonHomeFromAnAppJumpsAtOnce() {
        val w = HomeWatch()
        assertNull(w.opened("com.netflix.ninja", "Player", 1_000))
        assertEquals(0L, w.opened(amazon, amazonHome, 2_000))
    }

    @Test fun onePressJumpsOnce() {
        val w = HomeWatch()
        assertEquals(0L, w.opened(amazon, amazonHome, 1_000))
        assertNull(w.opened(amazon, amazonHome, 1_200))
        assertEquals(0L, w.opened(amazon, amazonHome, 2_000))
    }

    @Test fun otherAmazonLauncherScreensAreLeftAlone() {
        assertNull(HomeWatch().opened(amazon, "com.amazon.tv.launcher.ui.QuickSettingsActivity", 1_000))
    }

    @Test fun waitsWhenSettingsMayFollow() {
        val w = HomeWatch()
        w.opened("com.amazon.tv.settings.v2", "Settings", 1_000)
        assertEquals(HomeWatch.SETTINGS_WAIT_MS, w.opened(amazon, amazonHome, 2_000))
        w.opened("dev.plume", "dev.plume.MainActivity", 3_000)
        assertEquals(HomeWatch.SETTINGS_WAIT_MS, w.opened(amazon, amazonHome, 4_000))
    }

    @Test fun olderFireLauncher() {
        assertEquals(0L, HomeWatch().opened("com.amazon.firelauncher", "com.amazon.firelauncher.Launcher", 1_000))
    }

    @Test fun googleTvUnchanged() {
        val w = HomeWatch()
        val gtv = "com.google.android.apps.tv.launcherx"
        assertNull(w.opened(gtv, "com.google.android.apps.tv.launcherx.dashboard.DashboardActivity", 1_000))
        w.opened("dev.plume", "dev.plume.MainActivity", 2_000)
        assertEquals(0L, w.opened(gtv, "com.google.android.apps.tv.launcherx.home.HomeActivity", 3_000))
    }
}
