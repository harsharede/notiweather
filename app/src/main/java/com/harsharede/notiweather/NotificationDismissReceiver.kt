package com.harsharede.notiweather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when the user swipes away the weather notification. If updates are
 * still turned on, immediately queues a fresh [WeatherUpdateWorker] run so
 * the notification reappears with current data instead of staying gone
 * until the next scheduled refresh (up to 15 minutes later).
 *
 * Not every device honors this reliably — some OEM shades don't send the
 * delete broadcast for notifications in their "silent" bucket, which is why
 * [MainActivity] also double-checks and self-heals on resume.
 */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isEnabled(context)) return
        Scheduler.refreshNow(context)
    }
}
