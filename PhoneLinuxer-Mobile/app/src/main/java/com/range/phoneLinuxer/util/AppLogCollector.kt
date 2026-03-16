package com.range.phoneLinuxer.util

import androidx.compose.runtime.mutableStateListOf
import timber.log.Timber

object AppLogCollector : Timber.Tree() {
    val logs = mutableStateListOf<String>()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (logs.size > 200) logs.removeAt(0)

        val logTag = tag ?: "App"
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

        logs.add("[$timestamp] [$logTag]: $message")
    }
}