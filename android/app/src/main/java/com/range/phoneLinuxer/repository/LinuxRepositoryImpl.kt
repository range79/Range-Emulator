package com.range.phoneLinuxer.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.contentLength
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.readAvailable
import timber.log.Timber

class LinuxRepositoryImpl(
    private val context: Context,
    private val folderUri: Uri
) : LinuxRepository {

    private val client = HttpClient(CIO)

    override suspend fun downloadLinux(url: String, onProgress: (Int) -> Unit) {
        try {

            Timber.i("Download started: $url")

            val pickedDir = DocumentFile.fromTreeUri(context, folderUri)
                ?: throw Exception("Folder URI invalid")

            Timber.i("Selected folder: $folderUri")

            val fileName = url.substringAfterLast("/")
            val newFile = pickedDir.createFile("application/octet-stream", fileName)
                ?: throw Exception("Cannot create file")

            Timber.i("File created: ${newFile.uri}")

            val outputStream = context.contentResolver.openOutputStream(newFile.uri)
                ?: throw Exception("Cannot open output stream")

            val response = client.get(url)

            val total = response.contentLength() ?: -1L
            Timber.i("Total size: $total")

            val channel = response.bodyAsChannel()

            var bytesCopied = 0L
            var lastProgress = 0

            outputStream.use { output ->

                val buffer = ByteArray(8192)

                while (!channel.isClosedForRead) {

                    val read = channel.readAvailable(buffer)

                    if (read == -1) break

                    output.write(buffer, 0, read)
                    bytesCopied += read

                    if (total > 0) {

                        val progress = ((bytesCopied * 100) / total).toInt()

                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)

                            Timber.i("Download progress: $progress%")
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
    }}