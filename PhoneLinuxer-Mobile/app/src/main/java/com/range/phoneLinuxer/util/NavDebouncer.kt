package com.range.phoneLinuxer.util

object NavDebouncer {
    private var lastClickTime = 0L
    private const val DEBOUNCE_INTERVAL = 1000L

    fun canNavigate(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > DEBOUNCE_INTERVAL) {
            lastClickTime = currentTime
            return true
        }
        return false
    }
}