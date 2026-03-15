package com.range.phoneLinuxer.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.contentLength
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedOutputStream

class LinuxRepositoryImpl(
    private val context: Context,
    private val folderUri: Uri
) : LinuxRepository {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 60000
        }

        install(Logging) {
            level = LogLevel.INFO
        }
    }

    override suspend fun downloadLinux(url: String, onProgress: (Int) -> Unit) {

        try {
            Timber.i("Download started: $url")

            val pickedDir = DocumentFile.fromTreeUri(context, folderUri)
                ?: throw Exception("Folder URI invalid")

            val fileName = url.substringAfterLast("/")
            val newFile = pickedDir.createFile("application/octet-stream", fileName)
                ?: throw Exception("Cannot create file")

            Timber.i("File created: ${newFile.uri}")

            withContext(Dispatchers.IO) {

                context.contentResolver.openOutputStream(newFile.uri)?.use { rawOutput ->

                    BufferedOutputStream(rawOutput).use { output ->

                        client.prepareGet(url).execute { response ->

                            val totalBytes = response.contentLength() ?: -1L
                            Timber.i("Total size: $totalBytes")

                            val channel: ByteReadChannel = response.bodyAsChannel()

                            val buffer = ByteArray(64 * 1024)
                            var bytesCopied = 0L
                            var lastProgress = 0

                            while (!channel.isClosedForRead) {

                                val read = channel.readAvailable(buffer)

                                if (read <= 0) continue

                                output.write(buffer, 0, read)

                                bytesCopied += read

                                if (totalBytes > 0) {

                                    val progress =
                                        ((bytesCopied * 100) / totalBytes).toInt()

                                    if (progress - lastProgress >= 5) {
                                        lastProgress = progress
                                        onProgress(progress)
                                        Timber.i("Download progress: $progress%")
                                    }
                                }
                            }

                            output.flush()
                        }
                    }
                }
            }

            Timber.i("Download finished")
            onProgress(100)

        } catch (e: Exception) {

            Timber.e(e, "Download failed")
            onProgress(-1)
        }
    }
}