package com.harsharede.notiweather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.roundToInt

/**
 * Builds and shows the single ongoing weather notification: the current
 * temperature drawn as the small (status bar) icon, and current + next two
 * hours laid out in the expanded custom view.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "weather_updates"
    private const val NOTIFICATION_ID = 1001

    fun show(context: Context, weather: WeatherSnapshot) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val smallIcon = IconCompat.createWithBitmap(
            buildStatusBarIcon(weather.currentTemperature, weather.currentWeatherCode)
        )

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_expanded)
        remoteViews.setTextViewText(R.id.tv_now_label, context.getString(R.string.label_now))
        remoteViews.setTextViewText(R.id.tv_now_icon, WeatherCode.emoji(weather.currentWeatherCode))
        remoteViews.setTextViewText(R.id.tv_now_temp, formatTemp(weather.currentTemperature))

        val slots = listOf(
            Triple(R.id.tv_h1_label, R.id.tv_h1_icon, R.id.tv_h1_temp),
            Triple(R.id.tv_h2_label, R.id.tv_h2_icon, R.id.tv_h2_temp)
        )
        for ((index, slot) in slots.withIndex()) {
            val (labelId, iconId, tempId) = slot
            val point = weather.upcoming.getOrNull(index)
            if (point != null) {
                remoteViews.setTextViewText(labelId, formatHourLabel(point.time))
                remoteViews.setTextViewText(iconId, WeatherCode.emoji(point.weatherCode))
                remoteViews.setTextViewText(tempId, formatTemp(point.temperature))
            } else {
                remoteViews.setTextViewText(labelId, "")
                remoteViews.setTextViewText(iconId, "")
                remoteViews.setTextViewText(tempId, "")
            }
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(
                "${formatTemp(weather.currentTemperature)} · ${WeatherCode.description(weather.currentWeatherCode)}"
            )
            .setContentText(context.getString(R.string.notification_fallback_text))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(remoteViews)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatTemp(value: Double): String = "${value.roundToInt()}°"

    private fun formatHourLabel(isoTime: String): String = isoTime.substringAfter('T')

    /**
     * Draws the weather glyph and the rounded temperature (e.g. "18°") side
     * by side as the notification's small (status bar) icon. There's no
     * animated cycling between them — a continuously-updating status bar
     * icon would mean a timer running as long as the screen is on, which
     * defeats the point of only refreshing in the background — so both are
     * shown at once instead.
     *
     * The status bar only honors the alpha channel of a small icon (Android
     * renders every app's status bar icon as a plain white silhouette,
     * regardless of what colors are actually drawn here), so the weather
     * glyph shows up as a white outline rather than in color — the same way
     * the temperature digits already do.
     */
    private fun buildStatusBarIcon(temperature: Double, weatherCode: Int): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val tempText = formatTemp(temperature)
        val iconText = WeatherCode.emoji(weatherCode)

        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }

        val iconCenterX = size * 0.28f
        val tempCenterX = size * 0.70f
        fitTextSize(iconPaint, iconText, size * 0.5f, startSize = 56f)
        fitTextSize(tempPaint, tempText, size * 0.58f, startSize = 56f)

        drawCentered(canvas, iconText, iconPaint, iconCenterX, size / 2f)
        drawCentered(canvas, tempText, tempPaint, tempCenterX, size / 2f)

        return bitmap
    }

    private fun fitTextSize(paint: Paint, text: String, maxWidth: Float, startSize: Float) {
        var textSize = startSize
        paint.textSize = textSize
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        while (bounds.width() > maxWidth && textSize > 16f) {
            textSize -= 2f
            paint.textSize = textSize
            paint.getTextBounds(text, 0, text.length, bounds)
        }
    }

    private fun drawCentered(canvas: Canvas, text: String, paint: Paint, x: Float, y: Float) {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, x, y - bounds.exactCenterY(), paint)
    }
}
