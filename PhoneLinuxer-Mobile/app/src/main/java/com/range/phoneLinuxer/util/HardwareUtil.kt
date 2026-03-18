package com.range.phoneLinuxer.util

import android.app.ActivityManager
import android.content.Context
import java.io.File

object HardwareUtil {

    fun isKvmSupported(): Boolean {
        return try {
            val kvmDevice = File("/dev/kvm")
            kvmDevice.exists() && kvmDevice.canRead()
        } catch (e: Exception) {
            false
        }
    }

    fun getTotalRamMB(context: Context): Int {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            (memInfo.totalMem / (1024 * 1024)).toInt()
        } catch (e: Exception) {
            2048
        }
    }

    fun getTotalCores(): Int {
        return try {
            Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            4
        }
    }
}