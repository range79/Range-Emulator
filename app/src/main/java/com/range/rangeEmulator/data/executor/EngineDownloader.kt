package com.range.rangeEmulator.data.executor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class EngineDownloader(private val context: Context) {

    suspend fun download(
        url: String,
        allowMobileData: Boolean,
        onProgress: (Long, Long, Boolean, Boolean) -> Unit
    ) {
        try {
            if (!canDownload(allowMobileData)) {
                onProgress(0, 0, false, true)
                return
            }
            val zipFile = File(context.filesDir, "engine.zip")
            val startByte = if (zipFile.exists()) zipFile.length() else 0L

            var currentStartByte = startByte
            var retryCount = 0
            var isDownloadComplete = false
            var finalTotalBytes = 0L

            while (!isDownloadComplete && retryCount < 1000) {
                try {
                    withContext(Dispatchers.IO) {
                        val urlObj = java.net.URL(url)
                        var connection: java.net.HttpURLConnection? = null
                        try {
                            connection = urlObj.openConnection() as java.net.HttpURLConnection
                            if (currentStartByte > 0) {
                                connection.setRequestProperty("Range", "bytes=$currentStartByte-")
                            }
                            connection.connectTimeout = 30000
                            connection.readTimeout = 30000
                            connection.connect()

                            val code = connection.responseCode
                            if (code == 416) {
                                isDownloadComplete = true
                                return@withContext
                            }
                            if (code != 200 && code != 206) throw Exception("Server error: $code")

                            val contentLength = connection.contentLengthLong
                            if (finalTotalBytes == 0L && contentLength > 0) {
                                finalTotalBytes = if (code == 206) contentLength + currentStartByte else contentLength
                            }

                            connection.inputStream.use { input ->
                                java.io.FileOutputStream(zipFile, true).use { rawStream ->
                                    java.io.BufferedOutputStream(rawStream).use { output ->
                                        val buffer = ByteArray(64 * 1024)
                                        var lastEmitTime = 0L
                                        var read: Int

                                        while (input.read(buffer).also { read = it } != -1) {
                                            retryCount = 0
                                            if (!currentCoroutineContext().isActive) {
                                                isDownloadComplete = true
                                                break
                                            }
                                            if (!canDownload(allowMobileData)) {
                                                throw Exception("Mobile data restriction triggered")
                                            }
                                            output.write(buffer, 0, read)
                                            currentStartByte += read

                                            val now = System.currentTimeMillis()
                                            if (now - lastEmitTime >= 500 || currentStartByte == finalTotalBytes) {
                                                lastEmitTime = now
                                                onProgress(currentStartByte, finalTotalBytes, false, false)
                                            }
                                        }
                                        output.flush()

                                        if (currentStartByte >= finalTotalBytes || code == 200 && contentLength < 0) {
                                            isDownloadComplete = true
                                        }
                                    }
                                }
                            }
                        } finally {
                            connection?.disconnect()
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    e.printStackTrace()
                    retryCount++
                    if (retryCount >= 1000) throw e
                    delay(5000L)
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Timber.e(e, "Engine zip download failed")
                onProgress(0, 0, true, false)
            }
        }
    }

    fun canDownload(allowMobileData: Boolean): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        val isMobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        return when {
            isWifi -> true
            isMobile -> allowMobileData
            else -> false
        }
    }
}
