package com.range.phoneLinuxer.util

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
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
    fun getAvailableInternalStorageGB(context: Context): Float {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            bytesAvailable / (1024f * 1024f * 1024f)
        } catch (e: Exception) {
            0f
        }
    }

    fun getSafeStorageLimitGB(context: Context): Float {
        val available = getAvailableInternalStorageGB(context)
        return (available * 0.9f).coerceAtLeast(0f)
    }

}