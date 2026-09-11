package com.harsharede.notiweather

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns the on/off state of background weather updates. Uses WorkManager's
 * periodic work rather than a foreground service or an alarm loop: 15
 * minutes is WorkManager's minimum periodic interval, it naturally respects
 * Doze/App Standby, and it only wakes the app when the constraints (network
 * connected) are met, instead of polling on a fixed wall-clock timer.
 */
object Scheduler {

    private const val UNIQUE_WORK_NAME = "weather_update_periodic"

    fun start(context: Context) {
        Prefs.setEnabled(context, true)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                PeriodicWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        // Also run once immediately so the notification appears right away
        // instead of waiting up to 15 minutes for the first refresh.
        val oneOffRequest = OneTimeWorkRequestBuilder<WeatherUpdateWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(oneOffRequest)
    }

    fun stop(context: Context) {
        Prefs.setEnabled(context, false)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        NotificationHelper.clear(context)
    }

    fun isRunning(context: Context): Boolean = Prefs.isEnabled(context)
}
