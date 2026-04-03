package com.range.rangeEmulator.data.enums

import kotlinx.serialization.Serializable

@Serializable
enum class VmState {
    INACTIVE,

    STARTING,

    RUNNING,

    PAUSED,

    STOPPING,

    ERROR,

    PREPARING;

    fun isBusy(): Boolean = this == STARTING || this == STOPPING || this == PREPARING

    fun canStart(): Boolean = this == INACTIVE || this == ERROR

    fun canStop(): Boolean = this == RUNNING || this == PAUSED
}