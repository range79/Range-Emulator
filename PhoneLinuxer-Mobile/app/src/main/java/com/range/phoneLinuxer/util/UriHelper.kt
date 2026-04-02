package com.range.phoneLinuxer.util

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object UriHelper {

    suspend fun getRealPathFromUri(context: Context, uriString: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!uriString.startsWith("content://")) return@withContext uriString

        val uri = Uri.parse(uriString)
        val fileName = getFileName(context, uri) ?: "temp_iso_${System.currentTimeMillis()}.iso"
        val destFile = File(context.cacheDir, fileName)

        if (destFile.exists() && destFile.length() > 0) {
            // Already cached. In a real app we might want to check if it's the SAME file.
            return@withContext destFile.absolutePath
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext destFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy URI to cache: $uriString")
            return@withContext uriString // Fallback to original and let it fail in QEMU if necessary
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }
}
