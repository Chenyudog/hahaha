package com.cyd.timemaster.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cyd.timemaster.MainActivity
import com.cyd.timemaster.R

object Notifications {
    const val CHANNEL_TIMER = "timer"
    const val CHANNEL_REMINDER = "reminders"
    const val ID_TIMER = 1001
    const val ID_REMINDER_BASE = 2000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TIMER,
                context.getString(R.string.notif_channel_timer),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notif_channel_timer_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.notif_channel_reminder),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.notif_channel_reminder_desc) }
        )
    }

    fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 常驻计时通知：setUsesChronometer 让系统自动刷新已计时长，无需轮询 */
    fun timerNotification(context: Context, categoryName: String, startAt: Long): Notification =
        NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(categoryName)
            .setContentText("计时中 · 点击返回 App")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(startAt)
            .setContentIntent(contentIntent(context))
            .build()

    fun postReminder(context: Context, id: Int, title: String, text: String, openApp: Boolean = true) {
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        if (openApp) builder.setContentIntent(contentIntent(context))
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, builder.build())
    }
}
