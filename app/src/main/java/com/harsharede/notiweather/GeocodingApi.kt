package com.harsharede.notiweather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns a lat/lon into a short place name (e.g. "Hyderabad") using
 * OpenStreetMap's free, open, no-API-key Nominatim reverse geocoder. Failure
 * here (offline, rate limited, no address match) is not fatal to a weather
 * refresh — callers should treat a null result as "just don't show a place
 * name this time" rather than an error.
 */
object GeocodingApi {

    private const val BASE_URL = "https://nominatim.openstreetmap.org/reverse"
    private const val USER_AGENT = "NotiWeather/1.0 (+https://github.com/harsharede/notiweather)"

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "$BASE_URL?format=jsonv2&lat=$latitude&lon=$longitude&zoom=10&addressdetails=1"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parseName(body)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                null
            }
        }

    private fun parseName(body: String): String? {
        val root = JSONObject(body)

        val address = root.optJSONObject("address")
        if (address != null) {
            for (key in listOf("city", "town", "village", "suburb", "county", "state")) {
                val value = address.optString(key)
                if (value.isNotBlank()) return value
            }
        }

        return root.optString("display_name").substringBefore(",").ifBlank { null }
    }
}
