package com.range.phoneLinuxer.data.executor

import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.*

class EmulatorExecutor {
    private var process: Process? = null
    private val executorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logJob: Job? = null

    fun isRunning(): Boolean = process?.isAlive ?: false

    fun launch(command: List<String>) {
        if (isRunning()) {
            Timber.tag("QEMU").w("Emulator is already running!")
            return
        }

        executorScope.launch {
            try {
                Timber.tag("QEMU").i("Starting VM with command: ${command.joinToString(" ")}")

                val processBuilder = ProcessBuilder(command)
                processBuilder.redirectErrorStream(true)

                process = processBuilder.start()

                readLogs(process!!)

                val exitCode = process?.waitFor()
                Timber.tag("QEMU").i("Process exited with code: $exitCode")

            } catch (e: Exception) {
                Timber.tag("QEMU").e(e, "Failed to launch QEMU process")
            } finally {
                stop()
            }
        }
    }

    private fun readLogs(process: Process) {
        logJob?.cancel()
        logJob = executorScope.launch {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    var line: String? = null

                    while (isActive) {
                        line = reader.readLine()
                        if (line == null) break

                        Timber.tag("QEMU").d(line)
                    }
                }
            } catch (e: Exception) {
                Timber.tag("QEMU").e(e, "Error reading process output")
            }
        }
    }

    fun stop() {
        try {
            logJob?.cancel()
            process?.destroy()
            process = null
            Timber.tag("QEMU").w("Emulator process destroyed.")
        } catch (e: Exception) {
            Timber.tag("QEMU").e(e, "Error while destroying process")
        }
    }
}