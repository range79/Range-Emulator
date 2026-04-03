package com.range.rangeEmulator.data.model

import android.os.Environment
import com.range.rangeEmulator.data.enums.DesktopEnvironment
import kotlinx.serialization.Serializable

@Serializable
data class EasyInstallSettings (
    val username: String,
    val password: String,
    val desktopEnvironment: DesktopEnvironment
)