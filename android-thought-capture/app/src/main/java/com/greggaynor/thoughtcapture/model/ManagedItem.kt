package com.greggaynor.thoughtcapture.model

data class ManagedItem(
    val id: String,
    val title: String,
    val type: ManagedItemType,
    val category: String?,
    val parent: String?,
    val state: ItemState,
    val imagePath: String?,
    val notes: String?,
    val dateText: String?,
    val isEditable: Boolean,
    val sourceName: String
) {
    val isActive: Boolean
        get() = state == ItemState.ACTIVE
}
