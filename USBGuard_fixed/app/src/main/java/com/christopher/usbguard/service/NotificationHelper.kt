package com.christopher.usbguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.christopher.usbguard.R
import com.christopher.usbguard.model.RiskLevel
import com.christopher.usbguard.model.UsbSecurityEvent

object NotificationHelper {

    const val FOREGROUND_CHANNEL_ID = "usb_guard_foreground_v3"
    const val ALERT_CHANNEL_ID = "usb_guard_alert_no_vibration_v3"

    const val FOREGROUND_ID = 1001
    const val ALERT_ID = 1002

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val foregroundChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "USB Guard Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "USB Guard background monitoring"
                enableVibration(false)
                vibrationPattern = longArrayOf(0L)
                setSound(null, null)
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "USB Guard Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "USB security alerts without vibration"
                enableVibration(false)
                vibrationPattern = longArrayOf(0L)
                setSound(null, null)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(foregroundChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    fun foregroundNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("USB Guard Active")
            .setContentText("Monitoring USB power, data mode, and input devices")
            .setOngoing(true)
            .setSilent(true)
            .setVibrate(longArrayOf(0L))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showAlert(context: Context, event: UsbSecurityEvent) {
        val title = when (event.riskLevel) {
            RiskLevel.DANGER -> "Dangerous USB Activity"
            RiskLevel.WARNING -> "Suspicious USB Activity"
            else -> "USB Activity"
        }

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(event.detail.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)
            .setVibrate(longArrayOf(0L))
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(ALERT_ID, notification)
    }

    fun cancelAlerts(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(ALERT_ID)
    }
}
