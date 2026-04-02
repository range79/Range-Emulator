package com.range.phoneLinuxer.data.executor

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class EngineExtractor(private val context: Context) {

    suspend fun extract(onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val zipFile = File(context.filesDir, "engine.zip")
            if (!zipFile.exists()) return@withContext false

            val zf = java.util.zip.ZipFile(zipFile)
            val entries = zf.entries().toList()
            val totalEntries = entries.size
            if (totalEntries == 0) return@withContext false

            var count = 0
            var lastProgress = -1

            for (entry in entries) {
                var targetName = entry.name
                if (targetName.endsWith(".so")) {
                    var clean = targetName.removeSuffix(".so")
                    
                    clean = clean.replace("_so_", ".so.")
                    if (clean.endsWith("_so")) {
                        clean = clean.substringBeforeLast("_so") + ".so"
                    }
                    
                    val vRegex = "_(\\d+)".toRegex()
                    clean = vRegex.replace(clean) { ".${it.groupValues[1]}" }
                    
                    targetName = if (clean.contains(".so")) clean else "$clean.so"
                    targetName = targetName.replace("..", ".").removeSuffix(".")
                }

                val outFile = File(context.filesDir, targetName)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zf.getInputStream(entry).use { input ->
                        java.io.FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (entry.name.endsWith(".so") || entry.name.contains("qemu")) {
                        outFile.setExecutable(true, false)
                    }

                    if (targetName != entry.name && targetName.contains(".so.")) {
                        val unversionedName = targetName.substringBeforeLast(".so.") + ".so"
                        val unversionedFile = File(context.filesDir, unversionedName)
                        if (!unversionedFile.exists()) {
                            try { Os.symlink(outFile.absolutePath, unversionedFile.absolutePath) } catch (_: Exception) {}
                        }
                    }
                }
                count++
                val progress = ((count * 100) / totalEntries).coerceIn(0, 100)
                if (progress != lastProgress) {
                    lastProgress = progress
                    withContext(Dispatchers.Main) { onProgress(progress) }
                }
            }
            zf.close()
            zipFile.delete()

            val libzFile = File(context.filesDir, "libz.so.1")
            if (!libzFile.exists()) {
                Timber.e("CRITICAL: libz.so.1 not found after extraction! Ensure it's in the dependencies ZIP.")
            }

            true
        } catch (e: Exception) {
            Timber.e(e, "Extraction failed")
            false
        }
    }
}
