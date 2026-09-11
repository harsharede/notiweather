package com.harsharede.notiweather

/**
 * Maps Open-Meteo's WMO weather codes (WMO code table 4677) to a glyph and
 * short label. Using emoji instead of bundled art keeps the app dependency
 * free and lets the OS render the glyph in whatever style it already uses.
 */
object WeatherCode {

    fun emoji(code: Int): String = when (code) {
        0 -> "☀️" // clear sky
        1 -> "🌤️" // mainly clear
        2 -> "⛅" // partly cloudy
        3 -> "☁️" // overcast
        45, 48 -> "🌫️" // fog
        51, 53, 55 -> "🌦️" // drizzle
        56, 57 -> "🌧️" // freezing drizzle
        61, 63, 65 -> "🌧️" // rain
        66, 67 -> "🌧️" // freezing rain
        71, 73, 75, 77 -> "🌨️" // snow
        80, 81, 82 -> "🌦️" // rain showers
        85, 86 -> "🌨️" // snow showers
        95, 96, 99 -> "⛈️" // thunderstorm
        else -> "🌡️" // fallback: thermometer
    }

    fun description(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45 -> "Fog"
        48 -> "Rime fog"
        51 -> "Light drizzle"
        53 -> "Moderate drizzle"
        55 -> "Dense drizzle"
        56 -> "Light freezing drizzle"
        57 -> "Dense freezing drizzle"
        61 -> "Slight rain"
        63 -> "Moderate rain"
        65 -> "Heavy rain"
        66 -> "Light freezing rain"
        67 -> "Heavy freezing rain"
        71 -> "Slight snow"
        73 -> "Moderate snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Slight rain showers"
        81 -> "Moderate rain showers"
        82 -> "Violent rain showers"
        85 -> "Slight snow showers"
        86 -> "Heavy snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Weather"
    }
}
