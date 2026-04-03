package com.range.rangeEmulator.data.enums

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
        return when (this) {
            HOST -> "host"
            MAX -> "max"
            else -> this.name.lowercase().replace("_", "-")
        }
    }

    fun requiresKvm(): Boolean {
        return this == HOST
    }

    fun getPerformanceScore(): Int {
        return when (this) {
            HOST -> 100
            MAX -> 82
            NEOVERSE_N1 -> 78
            CORTEX_A76 -> 75
            CORTEX_A72 -> 68
            BROADWELL -> 55
            HASWELL -> 52
            IVYBRIDGE -> 48
            QEMU64 -> 45
            CORTEX_A57 -> 42
            CORTEX_A53 -> 30
            PENTIUM3 -> 22
            BASE -> 8
        }
    }

    fun getModeDescription(): String {
        val score = getPerformanceScore()
        return when (this) {
            HOST -> "[$score%] Native speed. Direct access to physical hardware. (Requires KVM)"
            MAX -> "[$score%] Maximum software speed. Enables all features for best non-KVM performance."
            NEOVERSE_N1 -> "[$score%] Modern server-grade ARM. Optimized for high-concurrency tasks."
            CORTEX_A76 -> "[$score%] High-performance mobile ARM. Recommended for modern Linux distros."
            CORTEX_A72 -> "[$score%] Balanced ARM emulation. Great for standard desktop environments."
            BROADWELL -> "[$score%] Advanced x86_64 emulation. High translation cost, but runs modern PC apps."
            HASWELL -> "[$score%] Solid x86_64 compatibility with AVX2 instruction support."
            IVYBRIDGE -> "[$score%] Legacy x86_64 performance. Stable for older Windows/Linux versions."
            QEMU64 -> "[$score%] Generic 64-bit CPU. Safest choice for maximum OS compatibility."
            CORTEX_A57 -> "[$score%] Older ARM-v8 emulation. Use for specific legacy ARM builds."
            CORTEX_A53 -> "[$score%] Efficiency-focused ARM. Very slow; suitable for lightweight tasks only."
            PENTIUM3 -> "[$score%] Legacy 32-bit speed. Best for Windows XP or ultra-light Linux."
            BASE -> "[$score%] Minimal instruction set. Used primarily for low-level kernel debugging."
        }
    }
}