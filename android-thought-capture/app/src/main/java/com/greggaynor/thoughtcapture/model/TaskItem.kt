package com.greggaynor.thoughtcapture.model

data class TaskItem(
    val id: String,
    val lineIndex: Int,
    val rawLine: String,
    val guid: String,
    val prefixTokens: List<String>,
    val title: String,
    val contexts: List<String>,
    val projects: List<String>,
    val areas: List<String>,
    val isSomeday: Boolean,
    val priority: String?,
    val dueDate: String?,
    val completedDate: String? = null
)
