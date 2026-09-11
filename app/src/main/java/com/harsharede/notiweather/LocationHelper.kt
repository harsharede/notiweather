package com.harsharede.notiweather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Resolves an approximate device location without depending on Google Play
 * services, so the app has no proprietary runtime dependency. Prefers a
 * recent cached fix (instant, no GPS/network wake-up); only asks the radios
 * for a fresh fix when nothing cached is available.
 */
object LocationHelper {

    private const val SINGLE_UPDATE_TIMEOUT_MS = 15_000L

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val cached = bestCachedLocation(locationManager)
        if (cached != null) return cached

        return requestSingleUpdate(locationManager)
    }

    private fun bestCachedLocation(locationManager: LocationManager): Location? {
        var best: Location? = null
        val providers = try {
            locationManager.getProviders(true)
        } catch (e: SecurityException) {
            return null
        }
        for (provider in providers) {
            val location = try {
                locationManager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            } ?: continue
            if (best == null || location.time > best.time) {
                best = location
            }
        }
        return best
    }

    private suspend fun requestSingleUpdate(locationManager: LocationManager): Location? =
        suspendCancellableCoroutine { continuation ->
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                continuation.resumeWith(Result.success(null))
                return@suspendCancellableCoroutine
            }

            val mainHandler = Handler(Looper.getMainLooper())
            var finished = false

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (finished) return
                    finished = true
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) continuation.resumeWith(Result.success(location))
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            val timeoutRunnable = Runnable {
                if (finished) return@Runnable
                finished = true
                locationManager.removeUpdates(listener)
                if (continuation.isActive) continuation.resumeWith(Result.success(null))
            }

            try {
                locationManager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                mainHandler.postDelayed(timeoutRunnable, SINGLE_UPDATE_TIMEOUT_MS)
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resumeWith(Result.success(null))
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
                mainHandler.removeCallbacks(timeoutRunnable)
            }
        }
}
