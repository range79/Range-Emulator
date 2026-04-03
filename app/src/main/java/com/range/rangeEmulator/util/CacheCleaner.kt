package com.range.rangeEmulator.util

import android.content.Context
import java.io.File
import timber.log.Timber
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class CacheCleaner(private val context: Context) {

    fun clearAllCache(): String {
        val beforeSize = getTotalCacheSize()
        val formattedSize = formatSize(beforeSize)

        try {
            context.cacheDir?.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()

            AppLogCollector.clear()

            Timber.i("Cache maintenance: %s was recovered.", formattedSize)
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear cache.")
        }
        return formattedSize
    }

    fun getTotalCacheSizeFormatted(): String {
        return formatSize(getTotalCacheSize())
    }

    private fun getTotalCacheSize(): Long {
        return getFolderSize(context.cacheDir) + getFolderSize(context.externalCacheDir)
    }

    private fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (!file.isDirectory) return file.length()
        return file.walkBottomUp().map { it.length() }.sum()
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0.00 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format(Locale.US, "%.2f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }
}