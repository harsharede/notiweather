package com.harsharede.notiweather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * WorkManager's own periodic work does not survive a device reboot, so this
 * re-enqueues it if the user had weather updates turned on before the
 * reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isEnabled(context)) {
            Scheduler.start(context)
        }
    }
}
