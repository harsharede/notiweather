package com.harsharede.notiweather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HourPoint(
    val time: String,
    val temperature: Double,
    val weatherCode: Int
)

data class WeatherSnapshot(
    val currentTemperature: Double,
    val currentWeatherCode: Int,
    val dailyHigh: Double,
    val dailyLow: Double,
    /** Local time, e.g. "06:23". */
    val sunrise: String,
    /** Local time, e.g. "18:45". */
    val sunset: String,
    /** The next hours after now, closest first. Usually 2 entries, may be fewer near a data boundary. */
    val upcoming: List<HourPoint>
)

/**
 * Thin client for the free, open, no-API-key Open-Meteo forecast API
 * (https://open-meteo.com, CC BY 4.0). Deliberately uses [HttpURLConnection]
 * and [org.json] rather than pulling in an HTTP/JSON library, since a single
 * GET request doesn't need one.
 */
object WeatherApi {

    private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherSnapshot =
        withContext(Dispatchers.IO) {
            val url = URL(
                "$BASE_URL?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code" +
                    "&hourly=temperature_2m,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                    "&forecast_days=2&timezone=auto"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            try {
                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Open-Meteo returned HTTP $responseCode")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                connection.disconnect()
            }
        }

    private fun parse(body: String): WeatherSnapshot {
        val root = JSONObject(body)

        val current = root.getJSONObject("current")
        val currentTemp = current.getDouble("temperature_2m")
        val currentCode = current.getInt("weather_code")
        val currentTime = current.getString("time")

        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")

        var startIndex = -1
        for (i in 0 until times.length()) {
            if (times.getString(i) == currentTime) {
                startIndex = i
                break
            }
        }
        if (startIndex == -1) {
            for (i in 0 until times.length()) {
                if (times.getString(i) > currentTime) {
                    startIndex = i
                    break
                }
            }
        }

        val upcoming = mutableListOf<HourPoint>()
        if (startIndex != -1) {
            for (offset in 1..2) {
                val index = startIndex + offset
                if (index < times.length()) {
                    upcoming.add(
                        HourPoint(
                            time = times.getString(index),
                            temperature = temps.getDouble(index),
                            weatherCode = codes.getInt(index)
                        )
                    )
                }
            }
        }

        val daily = root.getJSONObject("daily")

        return WeatherSnapshot(
            currentTemperature = currentTemp,
            currentWeatherCode = currentCode,
            dailyHigh = daily.getJSONArray("temperature_2m_max").getDouble(0),
            dailyLow = daily.getJSONArray("temperature_2m_min").getDouble(0),
            sunrise = daily.getJSONArray("sunrise").getString(0).substringAfter('T'),
            sunset = daily.getJSONArray("sunset").getString(0).substringAfter('T'),
            upcoming = upcoming
        )
    }
}
