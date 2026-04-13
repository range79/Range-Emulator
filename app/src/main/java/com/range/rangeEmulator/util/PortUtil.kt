package com.range.rangeEmulator.util

import java.net.ServerSocket
import timber.log.Timber

object PortUtil {

    private const val TAG = "PortUtil"

    fun findAvailablePort(startPort: Int, maxRetries: Int = 100): Int {
        var currentPort = startPort
        var retries = 0

        while (retries < maxRetries) {
            if (isPortAvailable(currentPort)) {
                return currentPort
            }
            currentPort++
            retries++
        }
        
        return try {
            ServerSocket(0).use { it.localPort }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to find any available port")
            startPort
        }
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use {
                it.reuseAddress = true
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
