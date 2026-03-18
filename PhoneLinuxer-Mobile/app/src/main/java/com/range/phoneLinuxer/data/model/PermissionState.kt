package com.range.phoneLinuxer.data.model

data class PermissionState(
    val isStorageGranted: Boolean = false,
    val isNotificationGranted: Boolean = false,
    val isBatteryOptimized: Boolean = false
) {
    val canStartApp: Boolean get() = isStorageGranted
}