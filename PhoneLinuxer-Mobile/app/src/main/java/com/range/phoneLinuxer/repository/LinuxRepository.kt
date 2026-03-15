package com.range.phoneLinuxer.repository

interface LinuxRepository {
    suspend fun downloadLinux(url: String, onProgress: (Int) -> Unit)
}