package dev.plume

/**
 * Decides, for each window that opens, whether Plume should cover the TV's own home screen. Fire OS never
 * hands the Home key to an accessibility service, so there this is the only way Home reaches Plume.
 * Kept free of Android classes so it can be tested off the TV.
 */
class HomeWatch {
    private var settingsMayFollow = false
    private var lastJump = -JUMP_GAP_MS

    /** Null: leave it (and drop any jump still waiting). Otherwise: go home after this many ms. */
    fun opened(pkg: String, cls: String?, now: Long): Long? {
        if (!isHome(pkg, cls)) {
            // Fire OS shows its home on the way into (and out of) Amazon Settings, then Settings on top.
            settingsMayFollow = pkg == PLUME || pkg.startsWith(AMAZON_SETTINGS)
            return null
        }
        if (now - lastJump < JUMP_GAP_MS) return null // one Home press sends several of these
        lastJump = now
        return if (settingsMayFollow && pkg != GOOGLE_TV) SETTINGS_WAIT_MS else 0
    }

    private fun isHome(pkg: String, cls: String?) = when (pkg) {
        GOOGLE_TV -> cls?.contains("dashboard", ignoreCase = true) != true // holding Home opens the dashboard
        AMAZON -> cls?.startsWith("$AMAZON.ui.HomeActivity") == true // HomeActivity_vNext on current builds
        OLD_AMAZON -> cls == "$OLD_AMAZON.Launcher"
        else -> false
    }

    companion object {
        private const val PLUME = "dev.plume"
        const val GOOGLE_TV = "com.google.android.apps.tv.launcherx"
        private const val AMAZON = "com.amazon.tv.launcher"
        private const val OLD_AMAZON = "com.amazon.firelauncher"
        private const val AMAZON_SETTINGS = "com.amazon.tv.settings"
        private const val JUMP_GAP_MS = 600L
        const val SETTINGS_WAIT_MS = 550L
    }
}
