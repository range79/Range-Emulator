 package com.range.rangeEmulator.util

import android.content.Context
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri

 object UriHelper {

    fun getRealPathFromUri(context: Context, uriString: String): String {
        if (!uriString.startsWith("content://")) return uriString

        val uri = uriString.toUri()
        getDirectPath(context, uri)?.let { path ->
            if (File(path).exists() && File(path).canRead()) {
                Timber.i("Resolved direct path: $path")
                return path
            }
        }
        
        // Final attempt: Use _data column directly if available
        getDataColumn(context, uri, null, null)?.let { path ->
            if (File(path).exists() && File(path).canRead()) {
                Timber.i("Resolved path from MediaStore: $path")
                return path
            }
        }

        return uriString
    }

    private fun getDirectPath(context: Context, uri: Uri): String? {
        val path = uri.path ?: return null
        val authority = uri.authority
        
        if (authority == "com.android.externalstorage.documents") {
            val docId = path.substringAfterLast("/document/", "")
            if (docId.startsWith("primary:")) {
                val subPath = docId.substringAfter("primary:")
                return "/storage/emulated/0/$subPath"
            }
            if (docId.contains(":")) {
                val parts = docId.split(":")
                return "/storage/${parts[0]}/${parts[1]}"
            }
        } else if (authority == "com.android.providers.downloads.documents") {
            val id = path.substringAfterLast("/document/", "")
            if (id.startsWith("raw:")) {
                return id.substringAfter("raw:")
            }
            return getDataColumn(context, uri, null, null)
        } else if (authority == "com.android.providers.media.documents") {
            val docId = path.substringAfterLast("/document/", "")
            val split = docId.split(":")
            if (split.size < 2) return null
            
            val type = split[0]
            val contentUri = when (type) {
                "image" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> android.provider.MediaStore.Files.getContentUri("external")
            }
            
            val selection = "_id=?"
            val selectionArgs = arrayOf(split[1])
            return getDataColumn(context, contentUri, selection, selectionArgs)
        }
        
        if (uri.scheme == "file") return uri.path
        
        return getDataColumn(context, uri, null, null)
    }

    private fun getDataColumn(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
        val column = "_data"
        val projection = arrayOf(column)
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(column)
                    if (index != -1) return cursor.getString(index)
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {}
        return name
    }
}
