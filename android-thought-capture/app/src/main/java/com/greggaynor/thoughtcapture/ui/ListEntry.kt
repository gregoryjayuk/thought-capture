package com.greggaynor.thoughtcapture.ui

import com.greggaynor.thoughtcapture.model.ManagedItem
import com.greggaynor.thoughtcapture.model.TaskItem

sealed interface ListEntry {
    data class Header(
        val title: String,
        val key: String = title,
        val badgeText: String? = null,
        val isCollapsible: Boolean = false,
        val isExpanded: Boolean = true
    ) : ListEntry
    data class AreaFocusTile(val item: ManagedItem, val summary: String) : ListEntry
    data class ItemRow(val item: ManagedItem) : ListEntry
    data class MilestoneRow(val item: ManagedItem, val ordinal: Int) : ListEntry
    data class TaskRow(val task: TaskItem) : ListEntry
}
