package com.range.rangeEmulator.data.repository
interface LinuxRepository {
    suspend fun downloadLinux(url: String, onProgress: (Long, Long, Boolean) -> Unit)
}