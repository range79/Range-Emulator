package com.range.phoneLinuxer.data.model

import android.os.Environment
import com.range.phoneLinuxer.data.enums.DesktopEnvironment
import kotlinx.serialization.Serializable

@Serializable
data class EasyInstallSettings (
    val username: String,
    val password: String,
    val desktopEnvironment: DesktopEnvironment
)