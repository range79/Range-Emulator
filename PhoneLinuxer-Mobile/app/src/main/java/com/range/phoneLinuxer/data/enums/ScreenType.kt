package com.range.phoneLinuxer.data.enums

import kotlinx.serialization.Serializable

@Serializable
enum class ScreenType {
    VNC,
    RDP;

    fun getDescription(): String {
        return when (this) {
            VNC -> "Universal compatibility. Standard for most Linux distros."
            RDP -> "High performance. Best for Windows or optimized Linux desktops."
        }
    }
}