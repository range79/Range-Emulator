package com.range.rangeEmulator.data.enums

enum class DarkModeEnum {
    LIGHT,
    DARK,
    SYSTEM;

    override fun toString(): String {
        return name.lowercase().replaceFirstChar { it.uppercase() }
    }
}