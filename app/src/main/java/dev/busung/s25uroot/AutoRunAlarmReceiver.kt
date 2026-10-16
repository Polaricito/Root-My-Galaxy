package dev.busung.s25uroot

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Alarm target for the boot auto-apply run.
 *
 * This used to point straight at [MainActivity], which meant starting an
 * activity from the background: blocked on Android 10+ unless an exemption
 * applies, and unreliable even when one does. The exploit also only applies
 * with the app in the foreground, so the run is announced instead of forced,
 * and the notification hands control to the activity.
 *
 * When the device is already unlocked the run is started directly as well,
 * since that is the one case where the background launch is both expected and
 * likely to succeed.
 */
class AutoRunAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AUTO_RUN_ALARM) return
        if (!AppPreferences.autoRunOnBoot(context)) return

        val status = AutoRunManager.status(context)
        if (status.suspended != null) {
            Log.d(TAG, "Auto-run is paused, dropping the alarm")
            return
        }

        val attempt = status.attempts + 1
        val posted = AutoRunNotifier.notifyRunReady(context, attempt, status.maxAttempts)
        Log.d(TAG, "Auto-run due (attempt $attempt/${status.maxAttempts}), notified=$posted")

        // Best effort: if the phone is in the user's hands the run can start
        // now, otherwise the notification is the way in.
        if (isDeviceUnlocked(context)) {
            AutoRunScheduler.openInstaller(context)
        }
    }

    private fun isDeviceUnlocked(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        // A device without a secure keyguard reports locked=false, which is
        // exactly the case where running straight away is safe.
        return keyguard?.isDeviceLocked == false
    }

    companion object {
        const val ACTION_AUTO_RUN_ALARM = "dev.busung.s25uroot.AUTO_RUN_ALARM"
        private const val TAG = "AutoRunAlarm"
    }
}