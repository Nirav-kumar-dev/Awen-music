package com.music.vivi.vivimusic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.music.vivi.MainActivity
import com.music.vivi.R
import com.music.vivi.constants.EnableNotificationsKey
import com.music.vivi.utils.dataStore
import com.music.vivi.utils.get

object UpdateNotificationHelper {
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 1001

    fun showUpdateNotification(context: Context, versionName: String, apkUrl: String? = null) {
        val notificationsEnabled = context.dataStore.get(EnableNotificationsKey, true)
        if (!notificationsEnabled) return

        val nm = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_updates_title),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when a new app update is available"
                enableLights(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val effectiveApkUrl = apkUrl ?: "https://github.com/Nirav-kumar-dev/TideFlow/releases/download/$versionName/TideFlow.apk"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = "vivi://update".toUri()
            putExtra("open_screen", "update")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openAppPendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, openAppIntent, flags)

        val downloadIntent = Intent(Intent.ACTION_VIEW, effectiveApkUrl.toUri())
        val downloadPendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID + 1, downloadIntent, flags)

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.vivimusicnotification)
            .setContentTitle("New Update Available: $versionName")
            .setContentText("Tap to view changelog and update TideFlow")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .addAction(
                R.drawable.download,
                "Download APK",
                downloadPendingIntent
            )
            .addAction(
                R.drawable.system_update_uptodate,
                "View Details",
                openAppPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
        }
    }

    fun showTestUpdateNotification(context: Context) {
        showUpdateNotification(context, "v1.0.0", "https://github.com/Nirav-kumar-dev/TideFlow/releases/download/v1.0.0/TideFlow.apk")
    }
}
