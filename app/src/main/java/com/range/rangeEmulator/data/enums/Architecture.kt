package com.range.rangeEmulator.data.enums

import kotlinx.serialization.Serializable

@Serializable
enum class Architecture {
    AARCH64,
    X86_64;

    override fun toString(): String {
        return when (this) {
            AARCH64 -> "ARM64 (aarch64)"
            X86_64 -> "x86_64 (Standard)"
        }
    }

    fun toQemuArch(): String {
        return when (this) {
            AARCH64 -> "aarch64"
            X86_64 -> "x86_64"
        }
    }
}
