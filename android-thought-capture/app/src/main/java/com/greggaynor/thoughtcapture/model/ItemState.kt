package com.greggaynor.thoughtcapture.model

enum class ItemState {
    ACTIVE,
    MAINTENANCE,
    INACTIVE;

    fun next(): ItemState = when (this) {
        ACTIVE -> MAINTENANCE
        MAINTENANCE -> INACTIVE
        INACTIVE -> ACTIVE
    }

    val isVisibleInActiveTab: Boolean
        get() = this == ACTIVE
}
