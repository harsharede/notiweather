package com.harsharede.notiweather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Fires when the user swipes away the weather notification. If updates are
 * still turned on, immediately queues a fresh [WeatherUpdateWorker] run so
 * the notification reappears with current data instead of staying gone
 * until the next scheduled refresh (up to 15 minutes later).
 */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isEnabled(context)) return

        val request = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
