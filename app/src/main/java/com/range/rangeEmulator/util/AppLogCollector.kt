package com.range.rangeEmulator.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object AppLogCollector : Timber.Tree() {
    val logs = mutableStateListOf<String>()

    private var logFile: File? = null
    @SuppressLint("ConstantLocale")
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        logFile = File(context.cacheDir, "app_logs.txt")

        if (logFile?.exists() == false) {
            logs.clear()
        } else {
            refreshLogsFromDisk()
        }
    }

    private fun refreshLogsFromDisk() {
        try {
            logFile?.let { file ->
                if (file.exists()) {
                    val lines = file.readLines().takeLast(200)
                    logs.clear()
                    logs.addAll(lines)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (logFile?.exists() == false && logs.isNotEmpty()) {
            logs.clear()
        }

        val logTag = tag ?: "App"
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] [$logTag]: $message"

        if (logs.size > 200) logs.removeAt(0)
        logs.add(logLine)

        saveToDisk(logLine)
    }

    private fun saveToDisk(line: String) {
        try {
            logFile?.let { file ->
                if (file.exists() && file.length() > 1024 * 1024) {
                    file.delete()
                    logs.clear()
                }

                FileOutputStream(file, true).use { out ->
                    out.write("$line\n".toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clear() {
        logs.clear()
        try {
            logFile?.let {
                if (it.exists()) it.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}