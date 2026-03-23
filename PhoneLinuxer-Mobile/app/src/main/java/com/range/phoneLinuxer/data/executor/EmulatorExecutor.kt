package com.range.phoneLinuxer.data.executor

import android.content.Context
import android.system.Os
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
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

class EmulatorExecutor(private val context: Context) {

    private val TAG = "EmulatorExecutor"
    private val executorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val runningProcesses = ConcurrentHashMap<String, Process>()
    private val logJobs = ConcurrentHashMap<String, Job>()
    private val _logStreams = ConcurrentHashMap<String, MutableSharedFlow<String>>()

    private val injectLibDir = File(context.filesDir, "injected_libs")

    private val systemBlacklist = listOf(
        "libEGL.so", "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so",
        "libvulkan.so", "libgui.so", "libui.so", "libandroid.so",
        "libutils.so", "libc++.so", "libm.so", "libc.so", "libdl.so"
    )

    fun getLogStream(vmId: String): SharedFlow<String> = _logStreams.getOrPut(vmId) {
        MutableSharedFlow(replay = 100, extraBufferCapacity = 500)
    }.asSharedFlow()

    private fun injectAndLinkLibs() {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        try {
            if (!injectLibDir.exists()) injectLibDir.mkdirs()
            
          
            if (File(injectLibDir, "libqemu_system.so").exists() && File(injectLibDir, "libz.so.1").exists()) return
            
            injectLibDir.listFiles()?.forEach { it.delete() }

            File(nativeLibDir).listFiles()?.forEach { file ->
                val fileName = file.name

                if (systemBlacklist.contains(fileName)) return@forEach

                if (fileName == "libz.so" || fileName == "libz_so_1.so") {
                    createSymlink(file, "libz.so.1")
                }

                if (fileName.contains("_so") && fileName.endsWith(".so")) {
                    val originalName = fileName.substringBeforeLast(".so")
                        .replace("_so", ".so")
                        .replace("_", ".")
                    createSymlink(file, originalName)
                }

                createSymlink(file, fileName)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Linking failed")
        }
    }

    private fun createSymlink(target: File, linkName: String) {
        val linkFile = File(injectLibDir, linkName)
        try {
            Os.symlink(target.absolutePath, linkFile.absolutePath)
        } catch (e: Exception) {
            Timber.tag(TAG).v("Symlink skip: $linkName")
        }
    }

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            socketTimeoutMillis = 30000
            connectTimeoutMillis = 60000
            requestTimeoutMillis = 100000
        }
    }

    suspend fun downloadEngineZip(
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

            withContext(Dispatchers.IO) {
                java.io.FileOutputStream(zipFile, true).use { rawStream ->
                    java.io.BufferedOutputStream(rawStream).use { output ->
                        httpClient.prepareGet(url) {
                            if (startByte > 0) {
                                header(HttpHeaders.Range, "bytes=$startByte-")
                            }
                        }.execute { response ->
                            if (!response.status.isSuccess() && response.status != HttpStatusCode.PartialContent) {
                                throw Exception("Server error: ${response.status.value}")
                            }

                            val contentLength = response.contentLength() ?: 0L
                            val totalBytes = if (startByte > 0) contentLength + startByte else contentLength

                            val channel = response.bodyAsChannel()
                            val buffer = ByteArray(64 * 1024)
                            var bytesCopied = startByte

                            while (!channel.isClosedForRead) {
                                if (!kotlinx.coroutines.currentCoroutineContext().isActive) break
                                if (!canDownload(allowMobileData)) {
                                    throw Exception("Mobile data restriction triggered")
                                }

                                val read = channel.readAvailable(buffer)
                                if (read == -1) break
                                if (read > 0) {
                                    output.write(buffer, 0, read)
                                    bytesCopied += read
                                    onProgress(bytesCopied, totalBytes, false, false)
                                }
                            }
                            output.flush()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val isCancelled = e is kotlinx.coroutines.CancellationException
            if (!isCancelled) {
                Timber.e(e, "Engine zip download failed")
                onProgress(0, 0, true, false)
            }
        }
    }

    private fun canDownload(allowMobileData: Boolean): Boolean {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val isMobile = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        val isWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)

        return when {
            isWifi -> true
            isMobile -> allowMobileData
            else -> false
        }
    }

    suspend fun extractEngineZip(onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
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
                    val clean = targetName.removeSuffix(".so")
                    val soIndex = clean.lastIndexOf("_so_")
                    if (soIndex != -1) {
                        val base = clean.substring(0, soIndex)
                        val rest = clean.substring(soIndex).replace("_", ".")
                        targetName = base + rest
                    } else if (clean.endsWith("_0_so")) {
                        targetName = clean.replace("_0_so", ".0.so")
                    }
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
            true
        } catch (e: Exception) {
            Timber.e(e, "Extraction failed")
            false
        }
    }

    suspend fun executeCommand(
        vmId: String,
        fullCommand: List<String>,
        onExit: ((Int) -> Unit)? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (isAlive(vmId)) return@withContext Result.failure(Exception("Already running"))

        try {
            injectAndLinkLibs()
            injectAndLinkLibs()

            val qemuBinary = File(context.applicationInfo.nativeLibraryDir, "libqemu_system.so")
            qemuBinary.setExecutable(true, false)
            
            val biosDir = File(context.filesDir, "pc-bios")
            val patchedCommand = fullCommand.toMutableList().apply {
                this[0] = qemuBinary.absolutePath
                add(1, "-L")
                add(2, biosDir.absolutePath)
            }

            val pb = ProcessBuilder(patchedCommand)
            val env = pb.environment()

            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val ldPath = "${injectLibDir.absolutePath}:${context.filesDir.absolutePath}:$nativeLibDir:/system/lib64:/vendor/lib64"
            env["LD_LIBRARY_PATH"] = ldPath

            val libz = File(injectLibDir, "libz.so.1")
            if (libz.exists()) {
                env["LD_PRELOAD"] = libz.absolutePath
                Timber.tag(TAG).i(" Force-loading libz: ${libz.absolutePath}")
            }

            env["PROOT_TMP_DIR"] = context.cacheDir.absolutePath
            env["TMPDIR"] = context.cacheDir.absolutePath
            env["HOME"] = context.filesDir.absolutePath

            pb.directory(context.filesDir)
            pb.redirectErrorStream(true)

            val process = pb.start()
            runningProcesses[vmId] = process
            logJobs[vmId] = launchLogReader(vmId, process.inputStream)

            if (onExit != null) {
                executorScope.launch {
                    try {
                        val exitCode = process.waitFor()
                        onExit(exitCode)
                    } catch (e: Exception) {
                        onExit(-1)
                    }
                }
            }

            Result.success(1L)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun launchLogReader(vmId: String, inputStream: InputStream): Job = executorScope.launch {
        val flow = _logStreams.getOrPut(vmId) { MutableSharedFlow(replay = 100, extraBufferCapacity = 500) }
        try {
            inputStream.bufferedReader().use { reader ->
                while (isActive) {
                    val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                    flow.emit(line)
                }
            }
        } finally { cleanup(vmId) }
    }

    fun killProcess(vmId: String) {
        try {
            logJobs[vmId]?.cancel()
            val process = runningProcesses[vmId]
            if (process != null) {
                try { process.destroy() } catch (t: Throwable) {}
                executorScope.launch {
                    try {
                        delay(500)
                        process.destroy()
                    } catch (t: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Kill process error")
        } finally {
            cleanup(vmId)
        }
    }

    fun killAll() {
        if (runningProcesses.isNotEmpty()) {
            runningProcesses.keys.forEach { killProcess(it) }
        }
    }

    private fun cleanup(vmId: String) {
        runningProcesses.remove(vmId)
        logJobs.remove(vmId)
    }

    fun isAlive(vmId: String): Boolean {
        val process = runningProcesses[vmId] ?: return false
        return try {
            process.exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        } catch (t: Throwable) {
            false
        }
    }
}