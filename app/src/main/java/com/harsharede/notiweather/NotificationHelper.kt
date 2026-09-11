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
 * Builds and shows the single weather notification: the current temperature
 * drawn as the small (status bar) icon; place name, today's high/low and
 * sunrise/sunset in the title/subtitle (visible whether the notification is
 * collapsed or expanded); and current + next two hours laid out in the
 * expanded custom view.
 *
 * Deliberately NOT marked ongoing: on some OEM shades (observed on Samsung
 * One UI) a swiped-away "ongoing" notification is removed through a path
 * that skips the standard dismiss callback entirely, so
 * [NotificationDismissReceiver]'s repost never fires. A plain, genuinely
 * dismissible notification goes through the normal flow instead, so the
 * delete intent reliably fires and the repost actually runs.
 */
object NotificationHelper {

    // "_v2": importance moved from LOW to DEFAULT (see ensureChannel) — an
    // already-created channel's importance can't be changed from code, so a
    // fresh channel ID is the only way to pick that change up on devices
    // that installed an earlier version.
    private const val CHANNEL_ID = "weather_updates_v2"
    private const val LEGACY_CHANNEL_ID = "weather_updates"
    private const val NOTIFICATION_ID = 1001

    fun show(context: Context, weather: WeatherSnapshot, locationName: String?) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val smallIcon = IconCompat.createWithBitmap(buildTemperatureIcon(weather.currentTemperature))

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

        val deleteIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NotificationDismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val locationPrefix = if (locationName.isNullOrBlank()) "" else "$locationName · "
        val title = "$locationPrefix${formatTemp(weather.currentTemperature)} · " +
            WeatherCode.description(weather.currentWeatherCode)
        val subtitle = "H:${formatTemp(weather.dailyHigh)} L:${formatTemp(weather.dailyLow)} · " +
            "↑${weather.sunrise} ↓${weather.sunset}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(remoteViews)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Whether the weather notification is currently visible. */
    fun isShowing(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any { it.id == NOTIFICATION_ID }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Low-importance channels land in the shade's collapsed "silent"
        // bucket on some OEM skins, where swipe-to-dismiss doesn't reliably
        // fire the delete broadcast NotificationDismissReceiver listens
        // for. Default importance keeps it in the normal list instead;
        // sound/vibration are silenced explicitly so it stays quiet.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatTemp(value: Double): String = "${value.roundToInt()}°"

    private fun formatHourLabel(isoTime: String): String = isoTime.substringAfter('T')

    /**
     * Draws the rounded temperature (e.g. "18°") as an opaque white glyph on
     * a transparent background. The status bar only honors the alpha channel
     * of a notification's small icon, so this renders as a plain silhouette
     * of the number — the same trick apps use to show a battery percentage
     * as their status bar icon.
     */
    private fun buildTemperatureIcon(temperature: Double): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val text = formatTemp(temperature)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

        var textSize = 60f
        paint.textSize = textSize
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        while (bounds.width() > size * 0.85f && textSize > 20f) {
            textSize -= 2f
            paint.textSize = textSize
            paint.getTextBounds(text, 0, text.length, bounds)
        }

        val y = size / 2f - bounds.exactCenterY()
        canvas.drawText(text, size / 2f, y, paint)
        return bitmap
    }
}
