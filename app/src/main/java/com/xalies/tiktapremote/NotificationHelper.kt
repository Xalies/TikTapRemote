package com.xalies.tiktapremote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

const val ALERT_CHANNEL_ID = "TTR_ALERT_CHANNEL"
const val CONTROL_CHANNEL_ID = "TTR_CONTROL_CHANNEL"
const val SET_TARGET_NOTIFICATION_ID = 1
const val RECORDING_NOTIFICATION_ID = 3
const val CONTROL_NOTIFICATION_ID = 2

// Updated to accept singleAction and doubleAction
fun showSetTargetNotification(
    context: Context,
    targetPackageName: String,
    targetAppName: String,
    keyCode: Int?,
    selectedTrigger: String,
    singleAction: String?,
    doubleAction: String?
) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(ALERT_CHANNEL_ID, "TikTap Remote Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "High-priority alerts for setting tap targets."
    }
    notificationManager.createNotificationChannel(channel)

    val intent = Intent(context, OverlayActivity::class.java).apply {
        putExtra("mode", "targeting")
        putExtra("targetPackageName", targetPackageName)
        putExtra("targetAppName", targetAppName)
        putExtra("selectedTrigger", selectedTrigger)
        keyCode?.let { putExtra("keyCode", it) }

        // Pass the actions to OverlayActivity so they are preserved
        if (singleAction != null) putExtra("singleAction", singleAction)
        if (doubleAction != null) putExtra("doubleAction", doubleAction)

        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("TikTap Remote")
        .setContentText("Ready to set tap target. Tap here to show the crosshair.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .build()

    notificationManager.notify(SET_TARGET_NOTIFICATION_ID, notification)
}

// Updated to accept singleAction and doubleAction
fun showRecordingNotification(
    context: Context,
    targetPackageName: String,
    targetAppName: String,
    selectedTrigger: String,
    singleAction: String?,
    doubleAction: String?
) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(ALERT_CHANNEL_ID, "TikTap Remote Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "High-priority alerts for recording gestures."
    }
    notificationManager.createNotificationChannel(channel)

    val intent = Intent(context, OverlayActivity::class.java).apply {
        putExtra("mode", "recording")
        putExtra("targetPackageName", targetPackageName)
        putExtra("targetAppName", targetAppName)
        putExtra("selectedTrigger", selectedTrigger)

        // Pass the actions to OverlayActivity so they are preserved
        if (singleAction != null) putExtra("singleAction", singleAction)
        if (doubleAction != null) putExtra("doubleAction", doubleAction)

        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

    val pendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("TikTap Remote")
        .setContentText("Ready to record gesture. Tap here to start.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .build()

    notificationManager.notify(RECORDING_NOTIFICATION_ID, notification)
}

fun cancelSetTargetNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(SET_TARGET_NOTIFICATION_ID)
    notificationManager.cancel(RECORDING_NOTIFICATION_ID)
}

fun showControlNotification(context: Context, isEnabled: Boolean) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(CONTROL_CHANNEL_ID, "TikTap Remote Control", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Persistent control for TikTap Remote service."
        setSound(null, null)
    }
    notificationManager.createNotificationChannel(channel)

    val toggleIntent = Intent(context, ServiceToggleReceiver::class.java)
    val togglePendingIntent = PendingIntent.getBroadcast(context, 0, toggleIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    val actionText = if (isEnabled) "Disable" else "Enable"
    val contentText = "TikTap Service is currently ${if (isEnabled) "ENABLED" else "DISABLED"}."

    val notification = NotificationCompat.Builder(context, CONTROL_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("TikTap Remote Control")
        .setContentText(contentText)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setOngoing(true)
        .addAction(0, actionText, togglePendingIntent)
        .build()

    notificationManager.notify(CONTROL_NOTIFICATION_ID, notification)
}

fun cancelControlNotification(context: Context) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(CONTROL_NOTIFICATION_ID)
}