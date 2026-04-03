package com.range.rangeEmulator.data.repository.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.range.rangeEmulator.data.model.AppSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream

class LinuxRepositoryImpl(
    private val context: Context,
    private val folderUri: Uri
) {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            socketTimeoutMillis = 30000
            connectTimeoutMillis = 60000
            requestTimeoutMillis = 100000
        }
    }

    suspend fun downloadLinux(
        url: String,
        settings: AppSettings,
        onProgress: (Long, Long, Boolean) -> Unit
    ) {
        try {
            if (!canDownload(settings)) {
                onProgress(0, 0, true)
                return
            }

            val pickedDir = DocumentFile.fromTreeUri(context, folderUri)
                ?: throw Exception("Invalid folder selection")

            val fileName = url.substringAfterLast("/")
            var file = pickedDir.findFile(fileName)

            val startByte = file?.length() ?: 0L

            if (file == null) {
                file = pickedDir.createFile("application/octet-stream", fileName)
                    ?: throw Exception("Could not create file")
            }

            var currentStartByte = startByte
            var retryCount = 0
            var isDownloadComplete = false
            var finalTotalBytes = 0L

            while (!isDownloadComplete && retryCount < 1000) {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(file.uri, "wa")?.use { rawStream ->
                            java.io.BufferedOutputStream(rawStream).use { output ->
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
                                    if (code != 200 && code != 206) {
                                        throw Exception("Server error: $code")
                                    }

                                    val contentLength = connection.contentLengthLong
                                    if (finalTotalBytes == 0L && contentLength > 0) {
                                        finalTotalBytes = if (code == 206) contentLength + currentStartByte else contentLength
                                    }

                                    connection.inputStream.use { input ->
                                        val buffer = ByteArray(64 * 1024)
                                        var lastEmitTime = 0L
                                        var read: Int

                                        while (input.read(buffer).also { read = it } != -1) {
                                            retryCount = 0
                                            if (!currentCoroutineContext().isActive) {
                                                isDownloadComplete = true
                                                break
                                            }
                                            if (!canDownload(settings)) {
                                                throw Exception("Mobile data restriction triggered")
                                            }

                                            output.write(buffer, 0, read)
                                            currentStartByte += read

                                            val now = System.currentTimeMillis()
                                            if (now - lastEmitTime >= 500 || currentStartByte == finalTotalBytes) {
                                                lastEmitTime = now
                                                onProgress(currentStartByte, finalTotalBytes, false)
                                            }
                                        }
                                        output.flush()

                                        if (currentStartByte >= finalTotalBytes || code == 200 && contentLength < 0) {
                                            isDownloadComplete = true
                                        }
                                    }
                                } finally {
                                    connection?.disconnect()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    e.printStackTrace()
                    retryCount++
                    if (retryCount >= 1000) throw e
                    kotlinx.coroutines.delay(5000L)
                }
            }
        } catch (e: Exception) {
            val isCancelled = e is kotlinx.coroutines.CancellationException
            if (!isCancelled) {
                e.printStackTrace()
                onProgress(0, 0, true)
            }
        }
    }

    private fun canDownload(settings: AppSettings): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val isMobile = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        return when {
            isWifi -> true
            isMobile -> settings.allowDownloadOnMobileData
            else -> false
        }
    }

    fun close() {
        client.close()
    }
}