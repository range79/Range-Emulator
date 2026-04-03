package com.range.rangeEmulator.data.enums

import kotlinx.serialization.Serializable

@Serializable
enum class ScreenType {
    VNC,
    SPICE;

    fun getDescription(): String {
        return when (this) {
            VNC -> "Universal compatibility. Standard for most Linux distros."
            SPICE -> "Optimized for Linux. Supports clipboard, folder sharing and guest agent."
        }
    }
}