package com.range.phoneLinuxer.data.executor

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

class EmulatorExecutor(private val context: Context) {

    private val TAG = "EmulatorExecutor"
    private val executorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val runningProcesses = ConcurrentHashMap<String, Process>()
    private val logJobs = ConcurrentHashMap<String, Job>()
    private val _logStreams = ConcurrentHashMap<String, MutableSharedFlow<String>>()

    private val libLinker = LibLinker(context)
    val downloader = EngineDownloader(context)
    val extractor = EngineExtractor(context)

    fun getLogStream(vmId: String): SharedFlow<String> = _logStreams.getOrPut(vmId) {
        MutableSharedFlow(replay = 100, extraBufferCapacity = 500)
    }.asSharedFlow()

    suspend fun downloadEngineZip(
        url: String,
        allowMobileData: Boolean,
        onProgress: (Long, Long, Boolean, Boolean) -> Unit
    ) = downloader.download(url, allowMobileData, onProgress)

    suspend fun extractEngineZip(onProgress: (Int) -> Unit): Boolean =
        extractor.extract(onProgress)

    suspend fun executeCommand(
        vmId: String,
        fullCommand: List<String>,
        onExit: ((Int) -> Unit)? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (isAlive(vmId)) return@withContext Result.failure(Exception("Already running"))

        try {
            libLinker.injectAndLink()

            val qemuInFilesDir = File(context.filesDir, "libqemu_system.so")
            val qemuInNativeDir = File(context.applicationInfo.nativeLibraryDir, "libqemu_system.so")
            val qemuBinary = when {
                qemuInNativeDir.exists() -> {
                    if (qemuInFilesDir.exists()) qemuInFilesDir.delete()
                    qemuInNativeDir
                }
                qemuInFilesDir.exists() -> qemuInFilesDir
                else -> throw Exception("QEMU engine not found. Please download the engine first.")
            }
            qemuBinary.setExecutable(true, false)

            val biosDir = File(context.filesDir, "pc-bios")
            val patchedCommand = mutableListOf<String>().apply {
                val linker = if (System.getProperty("os.arch")?.contains("64") == true) "/system/bin/linker64" else "/system/bin/linker"
                add(linker)
                add(qemuBinary.absolutePath)
                addAll(fullCommand.drop(1))
                add("-L")
                add(biosDir.absolutePath)
            }

            val pb = ProcessBuilder(patchedCommand)
            val env = pb.environment()

            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val injectLibDir = libLinker.injectLibDir
            
            val ldPath = "${injectLibDir.absolutePath}:${context.filesDir.absolutePath}:$nativeLibDir:/system/lib64:/vendor/lib64"
            env["LD_LIBRARY_PATH"] = ldPath

            val preloads = mutableListOf<String>()
            // Critical libs first: libz.so must be preloaded before libgnutls.so to satisfy verneed
            val criticalLibs = listOf(
                "libz.so.1", "libz.so",
                "libnettle.so.8", "libnettle.so",
                "libhogweed.so.6", "libhogweed.so",
                "libgmp.so",
                "libidn2.so", "libunistring.so",
                "libgnutls.so.30", "libgnutls.so",
                "libglib-2.0.so.0", "libglib-2.0.so"
            )

            criticalLibs.forEach { name ->
                val file = File(injectLibDir, name)
                if (file.exists()) preloads.add(file.absolutePath)
            }
            // NOTE: We intentionally do NOT add all injectLibDir files to LD_PRELOAD.
            // LD_LIBRARY_PATH already covers injectLibDir and filesDir, so all other
            // libs are found on-demand. Preloading everything causes Android system
            // libs (libunwindstack, libgui, etc.) to pick up wrong symbols.

            if (preloads.isNotEmpty()) {
                env["LD_PRELOAD"] = preloads.joinToString(":")
            }

            env["PROOT_TMP_DIR"] = context.cacheDir.absolutePath
            env["TMPDIR"] = context.cacheDir.absolutePath
            env["HOME"] = context.filesDir.absolutePath

            pb.directory(context.filesDir)
            pb.redirectErrorStream(true)

            _logStreams.getOrPut(vmId) { MutableSharedFlow(replay = 100, extraBufferCapacity = 500) }

            val process = pb.start()
            runningProcesses[vmId] = process
            logJobs[vmId] = launchLogReader(vmId, process.inputStream)

            if (onExit != null) {
                executorScope.launch {
                    try {
                        val exitCode = process.waitFor()
                        if (exitCode != 0 && exitCode != 143 && exitCode != 137) {
                            Timber.tag(TAG).e("VM $vmId exited with error code $exitCode")
                        }
                        onExit(exitCode)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "VM $vmId waitFor failed")
                        onExit(-1)
                    }
                }
            }

            Result.success(1L)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "executeCommand failed for $vmId")
            val flow = _logStreams.getOrPut(vmId) { MutableSharedFlow(replay = 100, extraBufferCapacity = 500) }
            executorScope.launch { flow.emit("LAUNCH ERROR: ${e.message}") }
            Result.failure(e)
        }
    }

    private fun launchLogReader(vmId: String, inputStream: InputStream): Job = executorScope.launch {
        val flow = _logStreams.getOrPut(vmId) { MutableSharedFlow(replay = 100, extraBufferCapacity = 500) }
        try {
            inputStream.bufferedReader().use { reader ->
                while (isActive) {
                    val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                    Timber.tag(TAG).d("[$vmId] $line")
                    flow.emit(line)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Log reader error for $vmId")
            flow.emit("LOG READER ERROR: ${e.message}")
        } finally { cleanup(vmId) }
    }

    fun killProcess(vmId: String) {
        try {
            logJobs[vmId]?.cancel()
            val process = runningProcesses[vmId]
            if (process != null) {
                try { process.destroy() } catch (t: Throwable) {}
                executorScope.launch {
                    try { delay(500); process.destroy() } catch (t: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Kill process error")
        } finally { cleanup(vmId) }
    }

    fun killAll() = runningProcesses.keys.forEach { killProcess(it) }

    fun isAlive(vmId: String): Boolean {
        val process = runningProcesses[vmId] ?: return false
        return try { process.exitValue(); false }
        catch (e: IllegalThreadStateException) { true }
        catch (t: Throwable) { false }
    }

    private fun cleanup(vmId: String) {
        runningProcesses.remove(vmId)
        logJobs.remove(vmId)
    }
}