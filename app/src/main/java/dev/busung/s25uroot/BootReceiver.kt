package dev.busung.s25uroot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Listens for boot broadcasts and arms the payload run.
 *
 * The run itself is delivered as an alarm instead of a background thread
 * because One UI aggressively kills long-lived background work; letting the
 * system wake the app at the scheduled time survives process death. QUICKBOOT
 * and REBOOT are handled alongside BOOT_COMPLETED because Samsung devices
 * emit those instead on some fast-boot paths.
 *
 * Booting only marks the run as waiting. The alarm is scheduled on
 * USER_PRESENT, because an exploit fired against a locked device is the
 * likeliest way to leave the phone unbootable.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            ACTION_REBOOT,
            -> {
                if (!AppPreferences.autoRunOnBoot(context)) return
                AutoRunManager.markAwaitingUnlock(context)
                AutoRunNotifier.cancel(context)
                Log.d(TAG, "Booted, waiting for the device to be unlocked")
            }

            Intent.ACTION_USER_PRESENT -> {
                // Consuming the flag keeps this to one run per boot instead of
                // one per screen-on.
                if (!AutoRunManager.consumeAwaitingUnlock(context)) return
                if (!AppPreferences.autoRunOnBoot(context)) return
                val delaySeconds = AppPreferences.bootDelay(context)
                if (AutoRunScheduler.schedule(context, delaySeconds)) {
                    Log.d(TAG, "Unlocked, scheduled auto-run payload in ${delaySeconds}s")
                } else {
                    Log.e(TAG, "Auto-run is armed but no alarm could be scheduled")
                }
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // An alarm queued by the previous build must not survive the
                // update: the next boot is the moment auto-run belongs to.
                AutoRunScheduler.cancel(context)
                if (AppPreferences.autoRunOnBoot(context)) {
                    Log.d(TAG, "App updated, auto-run re-armed for the next boot")
                }
            }

            else -> Unit
        }
    }

    companion object {
        const val ACTION_AUTO_RUN = "dev.busung.s25uroot.AUTO_RUN"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_REBOOT = "android.intent.action.REBOOT"
        private const val TAG = "BootReceiver"
    }
}