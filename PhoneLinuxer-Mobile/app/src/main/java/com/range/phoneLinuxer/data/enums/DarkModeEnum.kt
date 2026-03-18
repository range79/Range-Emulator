package com.range.phoneLinuxer.data.enums

enum class DarkModeEnum {
    LIGHT,
    DARK,
    SYSTEM;

    override fun toString(): String {
        return name.lowercase().replaceFirstChar { it.uppercase() }
    }
}