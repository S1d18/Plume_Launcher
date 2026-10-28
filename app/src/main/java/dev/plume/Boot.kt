package dev.plume

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/**
 * Yandex TV boots into its own launcher (it is direct-boot aware, Plume can't be), and something in the firmware
 * clears the enabled accessibility services soon after. So after boot Plume comes to the front itself, and its Home
 * service is put back whenever it is switched off by anything but Plume's own switch.
 */
class Boot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Log.i(TAG, "received ${i.action}")
        restoreHomeService(c)
        when (i.action) {
            // Yandex's own boot receiver brings its launcher back on top, so come forward again a little later.
            // This firmware doesn't send BOOT_COMPLETED to Plume at all; the clock being set right after boot does arrive.
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED -> if (justBooted()) { show(c); later(c, SHOW, SHOW_AGAIN_MS) }
            SHOW -> show(c)
        }
    }

    companion object {
        const val RESTORE = "dev.plume.RESTORE_HOME_SERVICE"
        const val SHOW = "dev.plume.SHOW_HOME"
        private const val TAG = "PlumeBoot"
        private const val SHOW_AGAIN_MS = 10_000L
        private const val RESTORE_DELAY_MS = 5_000L
        private const val WINDOW_MS = 10 * 60_000L
        private const val MAX_IN_WINDOW = 3
        private const val BOOT_WINDOW_MS = 3 * 60_000L

        /** Right after a cold start, when Yandex's launcher is what the TV opened on. */
        fun justBooted() = SystemClock.elapsedRealtime() < BOOT_WINDOW_MS

        fun show(c: Context) = c.startActivity(Intent(c, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        /** Needs the adb-granted WRITE_SECURE_SETTINGS; does nothing if the user turned Home off in Plume. */
        fun restoreHomeService(c: Context) {
            val prefs = c.getSharedPreferences("home", Context.MODE_PRIVATE)
            if (prefs.getBoolean(USER_OFF, false) ||
                c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return
            prefs.edit().remove("home_off").apply()
            val me = ComponentName(c, HomeOverride::class.java)
            val on = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            if (on.split(':').none { ComponentName.unflattenFromString(it) == me })
                Settings.Secure.putString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    if (on.isEmpty()) me.flattenToString() else "$on:${me.flattenToString()}")
            Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }

        /** The service was dropped from outside: try again shortly, a few times at most, so we never fight in a loop. */
        fun scheduleRestore(c: Context) {
            val prefs = c.getSharedPreferences("home", Context.MODE_PRIVATE)
            if (prefs.getBoolean(USER_OFF, false)) return
            val now = SystemClock.elapsedRealtime()
            val since = prefs.getLong("restore_since", 0L)
            val count = if (now - since in 0..WINDOW_MS) prefs.getInt("restore_count", 0) else 0
            if (count >= MAX_IN_WINDOW) return
            prefs.edit().putLong("restore_since", if (count == 0) now else since).putInt("restore_count", count + 1).apply()
            later(c, RESTORE, RESTORE_DELAY_MS)
        }

        private fun later(c: Context, action: String, ms: Long) {
            val pi = PendingIntent.getBroadcast(c, action.hashCode(), Intent(c, Boot::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            c.getSystemService(AlarmManager::class.java)?.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + ms, pi)
        }

        /** Set only by Plume's own Home switch; any other way the service goes off is undone. */
        const val USER_OFF = "home_off_user"
    }
}
