package com.harsharede.notiweather

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Runs on WorkManager's schedule (every ~15 minutes while the device is
 * unlocked and has a network, per the [Scheduler] constraints): resolves an
 * approximate location, fetches weather for it, and updates the persistent
 * notification. A no-op, not an error, whenever the feature has been turned
 * off or the app has no location fix yet — both are expected steady states,
 * not failures worth retrying aggressively.
 */
class WeatherUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!Prefs.isEnabled(applicationContext)) {
            return Result.success()
        }

        val location = LocationHelper.getLocation(applicationContext) ?: return Result.retry()

        return try {
            val weather = WeatherApi.fetchWeather(location.latitude, location.longitude)
            val locationName = GeocodingApi.reverseGeocode(location.latitude, location.longitude)
            NotificationHelper.show(applicationContext, weather, locationName)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
