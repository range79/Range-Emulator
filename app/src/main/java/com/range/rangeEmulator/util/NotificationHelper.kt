package com.range.rangeEmulator.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context


object NotificationHelper {
    const val CHANNEL_ID = "download_channel"
    const val NOTIFICATION_ID = 101

    fun createNotificationChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Linux Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of Linux ISO downloads"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}