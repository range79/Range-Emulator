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

    fun isUniversal(): Boolean = this == HOST

    fun getArch(): String {
        return when (this) {
            MAX, CORTEX_A76, CORTEX_A72, CORTEX_A57, CORTEX_A53, NEOVERSE_N1 -> "aarch64"
            QEMU64, IVYBRIDGE, HASWELL, BROADWELL, PENTIUM3, BASE -> "x86_64"
            else -> "universal"
        }
    }

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
            MAX -> 45
            NEOVERSE_N1 -> 42
            CORTEX_A76 -> 40
            CORTEX_A72 -> 35
            CORTEX_A57 -> 28
            CORTEX_A53 -> 18
            
            BROADWELL -> 15   
            HASWELL -> 12     
            IVYBRIDGE -> 10   
            QEMU64 -> 8       
            PENTIUM3 -> 5     
            BASE -> 2
        }
    }

    fun getModeDescription(): String {
        val score = getPerformanceScore()
        return when (this) {
            HOST -> "[$score%] Native speed. Direct access to physical hardware. (Requires KVM)"
            MAX -> "[$score%] Maximum software speed. Enables all features for best non-KVM performance."
            NEOVERSE_N1 -> "[ARM64] Modern server-grade ARM. Optimized for high-concurrency tasks."
            CORTEX_A76 -> "[ARM64] High-performance mobile ARM. Recommended for modern Linux distros."
            CORTEX_A72 -> "[ARM64] Balanced ARM emulation. Great for standard desktop environments."
            BROADWELL -> "[x86_64] Advanced x86_64 emulation. High translation cost, but runs modern PC apps."
            HASWELL -> "[x86_64] Solid x86_64 compatibility with AVX2 instruction support."
            IVYBRIDGE -> "[x86_64] Legacy x86_64 performance. Stable for older Windows/Linux versions."
            QEMU64 -> "[Universal] Generic 64-bit CPU. Safest choice for maximum OS compatibility."
            CORTEX_A57 -> "[ARM64] Older ARM-v8 emulation. Use for specific legacy ARM builds."
            CORTEX_A53 -> "[ARM64] Efficiency-focused ARM. Very slow; suitable for lightweight tasks only."
            PENTIUM3 -> "[x86] Legacy 32-bit speed. Best for Windows XP or ultra-light Linux."
            BASE -> "[x86] Minimal instruction set. Used primarily for low-level kernel debugging."
        }
    }
}