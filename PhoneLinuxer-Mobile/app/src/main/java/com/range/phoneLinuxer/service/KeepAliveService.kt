package com.range.phoneLinuxer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.content.Context
import android.app.PendingIntent
import android.net.wifi.WifiManager
import androidx.core.app.NotificationCompat
import com.range.phoneLinuxer.ui.activity.MainActivity
import timber.log.Timber

class KeepAliveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    companion object {
        const val MODE_DOWNLOAD = "mode_download"
        const val MODE_VM = "mode_vm"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_TEXT = "extra_text"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneLinuxer::KeepAlive")
            wakeLock?.acquire(24 * 60 * 60 * 1000L)

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PhoneLinuxer::WifiKeepAlive")
            wifiLock?.acquire()
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire locks")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_DOWNLOAD
        val customText = intent?.getStringExtra(EXTRA_TEXT)

        val channelId = "KEEP_ALIVE_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Process Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val title = if (mode == MODE_VM) "Virtual Machine Running" else "Download Active"
        val text = customText ?: (if (mode == MODE_VM) "Protecting VM process from background termination" else "Keeping connection alive in background")
        val icon = if (mode == MODE_VM) android.R.drawable.ic_media_play else android.R.drawable.stat_sys_download

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(101, notification)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service")
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wifiLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
