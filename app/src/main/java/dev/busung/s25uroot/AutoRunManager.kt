package dev.busung.s25uroot

import android.content.Context
import java.io.File

/**
 * Manages the auto-run on boot lifecycle including the bootloop failsafe.
 *
 * The failsafe works by recording the current boot_id before attempting
 * the exploit. If the device reboots unexpectedly during the exploit
 * (boot_id changes), the exploit was not successful and we auto-disable
 * auto-run to prevent a bootloop.
 */
object AutoRunManager {
    private const val PREFS = "auto_run"
    private const val BOOT_ID_KEY = "boot_id_before_exploit"
    private const val ATTEMPT_COUNTER_KEY = "boot_attempt_counter"
    private const val LAST_ATTEMPT_KEY = "last_boot_attempt_millis"

    /**
     * Record the current boot_id before launching the exploit,
     * so we can detect an unexpected reboot afterward.
     */
    fun saveBootIdBeforeExploit(context: Context): String? {
        val bootId = currentBootToken() ?: return null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(BOOT_ID_KEY, bootId)
            .apply()
        return bootId
    }

    /**
     * Check if the device rebooted unexpectedly (boot_id changed).
     * If so, the exploit did not complete successfully - this indicates
     * a bootloop condition, so we auto-disable auto-run.
     */
    fun checkAndHandleBootloop(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedBootId = prefs.getString(BOOT_ID_KEY, null) ?: return false
        val currentBootId = currentBootToken()

        if (savedBootId != currentBootId) {
            // Device rebooted unexpectedly - disable auto-run to prevent bootloop
            AppPreferences.setAutoRunOnBoot(context, false)
            prefs.edit().remove(BOOT_ID_KEY).apply()
            return true
        }

        // Boot_id matches - exploit likely completed, clean up
        prefs.edit().remove(BOOT_ID_KEY).apply()
        return false
    }

    /**
     * Record a successful auto-run attempt.
     */
    fun recordSuccess(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(BOOT_ID_KEY).apply()
    }

    /**
     * Increment the attempt counter (for additional safety tracking).
     */
    fun incrementAttemptCounter(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val counter = prefs.getInt(ATTEMPT_COUNTER_KEY, 0) + 1
        prefs.edit()
            .putInt(ATTEMPT_COUNTER_KEY, counter)
            .putLong(LAST_ATTEMPT_KEY, System.currentTimeMillis())
            .apply()
    }

    /**
     * Get the current attempt count.
     */
    fun getAttemptCount(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(ATTEMPT_COUNTER_KEY, 0)
    }

    /**
     * Reset the attempt counter after a successful run.
     */
    fun resetAttemptCounter(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(ATTEMPT_COUNTER_KEY, 0)
            .apply()
    }

    /**
     * Check if the auto-run was triggered from a boot event.
     */
    fun isAutoRunTriggered(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("auto_run_triggered", false)
    }

    /**
     * Mark that an auto-run attempt has been triggered.
     */
    fun setAutoRunTriggered(context: Context, triggered: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_run_triggered", triggered)
            .apply()
    }

    private fun currentBootToken(): String? {
        return runCatching {
            File("/proc/sys/kernel/random/boot_id")
                .readText()
                .trim()
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
