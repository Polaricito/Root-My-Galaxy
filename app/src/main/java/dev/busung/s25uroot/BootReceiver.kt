package dev.busung.s25uroot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.app.AlarmManager
import android.app.PendingIntent
import android.util.Log

/**
 * Listens for BOOT_COMPLETED and schedules the payload execution
 * after a configurable delay using AlarmManager.setExactAndAllowWhileIdle().
 *
 * This avoids keeping a background thread alive (which Samsung's One UI
 * aggressively kills) by letting the system wake the app at the exact
 * scheduled time.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AppPreferences.autoRunOnBoot(context)) return

        val delaySeconds = AppPreferences.bootDelay(context)
        val triggerAtMillis = SystemClock.elapsedRealtime() + (delaySeconds * 1000L)

        val scheduler = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getActivity(
            context,
            BOOT_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_AUTO_RUN
                putExtra(EXTRA_BOOT_DELAY, delaySeconds)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            scheduler.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
            Log.d(TAG, "Scheduled auto-run payload in ${delaySeconds}s after boot")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule boot alarm", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error scheduling boot alarm", e)
        }
    }

    companion object {
        const val ACTION_AUTO_RUN = "dev.busung.s25uroot.AUTO_RUN"
        const val EXTRA_BOOT_DELAY = "boot_delay_seconds"
        private const val BOOT_REQUEST_CODE = 42
        private const val TAG = "BootReceiver"

        fun schedule(context: Context, delaySeconds: Int) {
            val triggerAtMillis = SystemClock.elapsedRealtime() + (delaySeconds * 1000L)
            val scheduler = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getActivity(
                context,
                BOOT_REQUEST_CODE,
                Intent(context, MainActivity::class.java).apply {
                    action = ACTION_AUTO_RUN
                    putExtra(EXTRA_BOOT_DELAY, delaySeconds)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            try {
                scheduler.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to schedule boot alarm", e)
            }
        }

        fun cancel(context: Context) {
            val scheduler = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getActivity(
                context,
                BOOT_REQUEST_CODE,
                Intent(context, MainActivity::class.java).apply {
                    action = ACTION_AUTO_RUN
                },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pendingIntent != null) {
                scheduler.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
