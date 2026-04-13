package com.range.rangeEmulator.data.executor

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.range.rangeEmulator.data.enums.Architecture
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
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val swtpmProcesses = ConcurrentHashMap<String, Process>()
    private val cleanupLock = Any()
    private val libLinker = LibLinker(context)
    val downloader = EngineDownloader(context)
    val extractor = EngineExtractor(context)

    fun getLogStream(vmId: String): SharedFlow<String> = _logStreams.getOrPut(vmId) {
        MutableSharedFlow(replay = 100, extraBufferCapacity = 500)
    }.asSharedFlow()

    suspend fun downloadZip(
        url: String,
        targetFileName: String,
        allowMobileData: Boolean,
        onProgress: (Long, Long, Boolean, Boolean) -> Unit
    ) = downloader.download(url, targetFileName, allowMobileData, onProgress)

    suspend fun extractZip(fileName: String, onProgress: (Int) -> Unit): Boolean =
        extractor.extract(fileName, onProgress)

    suspend fun createDiskImage(
        path: String,
        format: String,
        sizeGB: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            libLinker.injectAndLink()
            
            val injectLibDir = libLinker.injectLibDir
            val qemuImgNative = File(context.applicationInfo.nativeLibraryDir, "qemu-img")
            val qemuImgLibNative = File(context.applicationInfo.nativeLibraryDir, "libqemu-img.so")
            val qemuImgFiles = File(context.filesDir, "qemu-img")
            val qemuImgLibFiles = File(context.filesDir, "libqemu-img.so")
            
            val qemuImgBinary = when {
                qemuImgNative.exists() -> qemuImgNative
                qemuImgLibNative.exists() -> qemuImgLibNative
                qemuImgFiles.exists() -> qemuImgFiles
                qemuImgLibFiles.exists() -> qemuImgLibFiles
                else -> null
            } ?: return@withContext Result.failure(Exception("qemu-img binary not found"))

            qemuImgBinary.setExecutable(true, false)

            val cmd = mutableListOf<String>().apply {
                val linker = if (System.getProperty("os.arch")?.contains("64") == true) "/system/bin/linker64" else "/system/bin/linker"
                add(linker)
                add(qemuImgBinary.absolutePath)
                add("create")
                add("-f")
                add(format.lowercase())
                add(path)
                add("${sizeGB}G")
            }

            val pb = ProcessBuilder(cmd)
            val env = pb.environment()
            
            val linkerEnv = libLinker.getEnvironment()
            env.putAll(linkerEnv)
            
            env["LD_LIBRARY_PATH"] = "${env["LD_LIBRARY_PATH"]}:/system/lib64:/vendor/lib64"

            val process = pb.start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) Result.success(Unit)
            else {
                val error = process.errorStream.bufferedReader().readText()
                Result.failure(Exception("qemu-img failed ($exitCode): $error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeCommand(
        vmId: String,
        fullCommand: List<String>,
        isTpmEnabled: Boolean = false,
        tpmSockPath: String? = null,
        arch: Architecture = Architecture.AARCH64,
        onExit: ((Int) -> Unit)? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        // Force kill any existing process for this VM to ensure a clean start
        if (isAlive(vmId)) {
            Timber.tag(TAG).w("VM $vmId is already running/stale. Force killing before restart...")
            killProcess(vmId)
            delay(500) // Small delay to let the OS clean up
        }

        try {
            libLinker.injectAndLink()

            if (isTpmEnabled && tpmSockPath != null) {
                launchSwtpm(vmId, tpmSockPath)
                
                val socketFile = File(tpmSockPath)
                var attempts = 0
                while (!socketFile.exists() && attempts < 20) {
                    delay(100)
                    attempts++
                }
                
                if (!socketFile.exists()) {
                    Timber.tag(TAG).e("TPM socket failed to initialize at $tpmSockPath")
                } else {
                    Timber.tag(TAG).d("TPM socket ready after ${attempts * 100}ms")
                }
            }

            val qemuArch = arch.toQemuArch()
            val archBinaryName = "libqemu_system_$qemuArch.so"
            
            Timber.tag(TAG).i("Target Architecture: ${arch.name} ($qemuArch)")

            val qemuInFilesDir = File(context.filesDir, archBinaryName)
            val qemuInNativeDir = File(context.applicationInfo.nativeLibraryDir, archBinaryName)
            val fallbackFilesDir = File(context.filesDir, "libqemu_system.so")
            val fallbackNativeDir = File(context.applicationInfo.nativeLibraryDir, "libqemu_system.so")
            
            val qemuBinary = when {
                qemuInNativeDir.exists() -> qemuInNativeDir
                qemuInFilesDir.exists() -> qemuInFilesDir
                fallbackNativeDir.exists() -> fallbackNativeDir
                fallbackFilesDir.exists() -> fallbackFilesDir
                else -> throw Exception("QEMU engine for $qemuArch not found. Please download the engine first.")
            }
            qemuBinary.setExecutable(true, false)

            Timber.tag(TAG).i("Engine path: ${qemuBinary.absolutePath}")

            val biosDir = File(context.filesDir, "pc-bios")
            val patchedCommand = mutableListOf<String>().apply {
                val linker = if (System.getProperty("os.arch")?.contains("64") == true) "/system/bin/linker64" else "/system/bin/linker"
                add(linker)
                add(qemuBinary.absolutePath)
                addAll(fullCommand.drop(1))
                add("-L")
                add(biosDir.absolutePath)
            }

            Timber.tag(TAG).i("FULL COMMAND: ${patchedCommand.joinToString(" ")}")

            val pb = ProcessBuilder(patchedCommand)
            val env = pb.environment()

            val linkerEnv = libLinker.getEnvironment()
            env.putAll(linkerEnv)
            env["LD_LIBRARY_PATH"] = "${env["LD_LIBRARY_PATH"]}:/system/lib64:/vendor/lib64"

            env["PROOT_TMP_DIR"] = context.cacheDir.absolutePath
            env["TMPDIR"] = context.cacheDir.absolutePath
            env["HOME"] = context.filesDir.absolutePath

            pb.directory(context.filesDir)
            pb.redirectErrorStream(true)

            _logStreams.getOrPut(vmId) { MutableSharedFlow(replay = 100, extraBufferCapacity = 500) }

            val process = pb.start()
            runningProcesses[vmId] = process
            logJobs[vmId] = launchLogReader(vmId, process.inputStream)

            // Performance Booster is now standard for all modes
            launchPerformanceBooster(vmId, process)

            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RangeEmulator:VM_RUNNING").apply {
                    acquire()
                }
            }

            if (onExit != null) {
                executorScope.launch {
                    try {
                        val exitCode = process.waitFor()
                        onExit(exitCode)
                    } catch (e: Exception) {
                        onExit(-1)
                    } finally {
                        cleanup(vmId)
                    }
                }
            } else {
                executorScope.launch {
                    try {
                        process.waitFor()
                    } finally {
                        cleanup(vmId)
                    }
                }
            }

            Result.success(1L)
        } catch (e: Exception) {
            val flow = _logStreams.getOrPut(vmId) { MutableSharedFlow(replay = 100, extraBufferCapacity = 500) }
            executorScope.launch { flow.emit("LAUNCH ERROR: ${e.message}") }
            Result.failure(e)
        }
    }

    private fun launchPerformanceBooster(vmId: String, process: Process) = executorScope.launch {
        val pid = getProcessPid(process)
        if (pid <= 0) return@launch

        val boostedTids = mutableSetOf<Int>()
        var adpfInitialized = false

        while (isActive && isAlive(vmId)) {
            try {
                val taskDir = File("/proc/$pid/task")
                if (taskDir.exists()) {
                    val currentTids = taskDir.listFiles()?.mapNotNull { it.name.toIntOrNull() } ?: emptyList()
                    
                    currentTids.forEach { tid ->
                        if (tid !in boostedTids) {
                            android.os.Process.setThreadPriority(tid, -19)
                            boostedTids.add(tid)
                        }
                    }

                    if (!adpfInitialized && currentTids.isNotEmpty()) {
                        adpfInitialized = com.range.rangeEmulator.util.PerformanceHintManagerHelper.createPerformanceSession(
                            context,
                            currentTids.toIntArray()
                        )
                    }

                    if (adpfInitialized) {
                        com.range.rangeEmulator.util.PerformanceHintManagerHelper.reportHeavyWork()
                    }
                }
            } catch (e: Exception) {}
            delay(3000)
        }
        
        if (adpfInitialized) {
            com.range.rangeEmulator.util.PerformanceHintManagerHelper.closeSession()
        }
    }

    private fun getProcessPid(process: Process): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pidMethod = process.javaClass.getMethod("pid")
                (pidMethod.invoke(process) as Long).toInt()
            } else {
                val field = process.javaClass.getDeclaredField("pid")
                field.isAccessible = true
                field.getInt(process)
            }
        } catch (e: Exception) {
            -1
        }
    }

    private fun launchLogReader(vmId: String, inputStream: InputStream): Job = executorScope.launch {
        val flow = _logStreams.getOrPut(vmId) { MutableSharedFlow(replay = 100, extraBufferCapacity = 500) }
        try {
            inputStream.bufferedReader().use { reader ->
                while (isActive) {
                    val line = try {
                        withContext(Dispatchers.IO) { reader.readLine() }
                    } catch (e: java.io.IOException) {
                        null
                    } catch (e: Exception) {
                        null
                    } ?: break
                    
                    flow.emit(line)
                }
            }
        } catch (e: Exception) {
            if (e !is java.io.IOException && e !is java.util.concurrent.CancellationException) {
                flow.emit("LOG READER ERROR: ${e.message}")
            }
        } finally { cleanup(vmId) }
    }

    fun killProcess(vmId: String) {
        try {
            logJobs[vmId]?.cancel()
            
            swtpmProcesses[vmId]?.let {
                try { it.destroy() } catch (_: Throwable) {}
            }
            
            val process = runningProcesses[vmId]
            if (process != null) {
                try { process.destroy() } catch (t: Throwable) {}
                executorScope.launch {
                    try { delay(500); process.destroy() } catch (t: Throwable) {}
                }
            }
        } catch (t: Throwable) {
        } finally { cleanup(vmId) }
    }

    fun killAll() = runningProcesses.keys.forEach { killProcess(it) }

    fun isAlive(vmId: String): Boolean {
        val qemuProcess = runningProcesses[vmId]
        val swtpmProcess = swtpmProcesses[vmId]
        
        val qemuAlive = qemuProcess != null && try { processAlive(qemuProcess) } catch (e: Exception) { false }
        val swtpmAlive = swtpmProcess != null && try { processAlive(swtpmProcess) } catch (e: Exception) { false }
        
        return qemuAlive || swtpmAlive
    }

    private fun processAlive(process: Process): Boolean {
        return try {
            process.exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
    }

    fun hasRunningProcesses(): Boolean = runningProcesses.isNotEmpty()

    private fun launchSwtpm(vmId: String, sockPath: String) {
        val swtpmBinary = File(context.applicationInfo.nativeLibraryDir, "libswtpm.so")
        if (!swtpmBinary.exists()) {
            Timber.e("swtpm binary not found at ${swtpmBinary.absolutePath}")
            return
        }
        swtpmBinary.setExecutable(true, false)

        val tpmStateDir = File(context.filesDir, "tpm/$vmId")
        tpmStateDir.mkdirs()

        val injectLibDir = libLinker.injectLibDir
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val ldPath = "${injectLibDir.absolutePath}:${context.filesDir.absolutePath}:$nativeLibDir:/system/lib64:/vendor/lib64"

        val cmd = mutableListOf<String>().apply {
            val linker = if (System.getProperty("os.arch")?.contains("64") == true) "/system/bin/linker64" else "/system/bin/linker"
            add(linker)
            add(swtpmBinary.absolutePath)
            add("socket")
            add("--tpmstate")
            add("dir=${tpmStateDir.absolutePath}")
            add("--ctrl")
            add("type=unixio,path=$sockPath")
            add("--tpm2")
            add("--log")
            add("level=20")
        }

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        val env = pb.environment()
        
        env["LD_LIBRARY_PATH"] = ldPath
        env["TMPDIR"] = context.cacheDir.absolutePath
        env["HOME"] = context.filesDir.absolutePath
        
        try {
            val process = pb.start()
            swtpmProcesses[vmId] = process
            
            executorScope.launch {
                try {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            Timber.tag("SWTPM_$vmId").d(line)
                        }
                    }
                } catch (e: Exception) {
                    if (e !is java.io.IOException && e !is java.util.concurrent.CancellationException) {
                        Timber.tag("SWTPM_$vmId").e(e, "Log reader failed")
                    }
                } finally {
                    try {
                        val exitCode = process.waitFor()
                        Timber.tag("SWTPM_$vmId").i("Process exited with code: $exitCode")
                    } catch (_: Exception) {}
                    cleanup(vmId)
                }
            }
            Timber.tag(TAG).d("Started swtpm for $vmId at $sockPath")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to launch swtpm")
        }
    }

    private fun cleanup(vmId: String) = synchronized(cleanupLock) {
        val hadProcess = runningProcesses.containsKey(vmId) || swtpmProcesses.containsKey(vmId)
        
        runningProcesses.remove(vmId)
        logJobs.remove(vmId)
        
        swtpmProcesses[vmId]?.let {
            try { it.destroy() } catch (_: Throwable) {}
            swtpmProcesses.remove(vmId)
        }
        
        if (runningProcesses.isEmpty()) {
            wakeLock?.let {
                try {
                    if (it.isHeld) it.release()
                } catch (_: Throwable) {}
                wakeLock = null
            }
        }
    }
}