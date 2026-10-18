package com.greggaynor.thoughtcapture.model

data class NoteDetail(
    val id: String,
    val title: String,
    val lines: List<NoteLine>,
    val updatedAt: String?
)

data class NoteLine(
    val id: String,
    val text: String,
    val isChecked: Boolean,
    val updatedAt: String?
)
