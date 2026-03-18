package com.range.phoneLinuxer.data.enums

import kotlinx.serialization.Serializable

@Serializable
enum class CpuModel {
    HOST,
    MAX,

    CORTEX_A76,
    CORTEX_A72,
    CORTEX_A57,
    CORTEX_A53,
    NEOVERSE_N1,

    QEMU64,
    IVYBRIDGE,
    HASWELL,
    BROADWELL,
    PENTIUM3,

    BASE;

    fun toQemuParam(): String {
        return this.name.lowercase().replace("_", "-")
    }
}