package com.range.rangeEmulator.data.model

data class LinuxDistro(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val iconResId: Int? = null
)
