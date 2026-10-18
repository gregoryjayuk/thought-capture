package com.greggaynor.thoughtcapture.data

import android.content.ContentResolver
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.greggaynor.thoughtcapture.TranscriptStore
import com.greggaynor.thoughtcapture.model.ItemState
import com.greggaynor.thoughtcapture.model.ManagedItem
import com.greggaynor.thoughtcapture.model.ManagedItemType
import com.greggaynor.thoughtcapture.model.NoteDetail
import com.greggaynor.thoughtcapture.model.NoteLine
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class NoteRepository(
    private val context: Context,
    private val contentResolver: ContentResolver
) {
    fun loadNoteItems(): List<ManagedItem> {
        return readNotes().map { note ->
            val openCount = note.lines.count { !it.isChecked }
            ManagedItem(
                id = note.id,
                title = note.title,
                type = ManagedItemType.NOTE,
                category = null,
                parent = if (note.lines.isEmpty()) {
                    null
                } else {
                    "$openCount open  •  ${note.lines.size} lines"
                },
                state = ItemState.ACTIVE,
                imagePath = null,
                notes = null,
                dateText = null,
                isEditable = true,
                sourceName = SOURCE_NAME
            )
        }.sortedBy { it.title.lowercase() }
    }

    fun loadNote(noteId: String): NoteDetail? = readNotes().firstOrNull { it.id == noteId }

    fun createNote(title: String): NoteDetail {
        val note = NoteDetail(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            lines = emptyList(),
            updatedAt = nowStamp()
        )
        writeNotes(readNotes() + note)
        return note
    }

    fun renameNote(noteId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        writeNotes(
            readNotes().map { note ->
                if (note.id == noteId) note.copy(title = trimmed, updatedAt = nowStamp()) else note
            }
        )
    }

    fun deleteNote(noteId: String) {
        writeNotes(readNotes().filterNot { it.id == noteId })
    }

    fun addLine(noteId: String, text: String): NoteDetail? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return loadNote(noteId)
        val stamp = nowStamp()
        var updatedNote: NoteDetail? = null
        writeNotes(
            readNotes().map { note ->
                if (note.id == noteId) {
                    note.copy(
                        lines = note.lines + NoteLine(
                            id = UUID.randomUUID().toString(),
                            text = trimmed,
                            isChecked = false,
                            updatedAt = stamp
                        ),
                        updatedAt = stamp
                    ).also { updatedNote = it }
                } else {
                    note
                }
            }
        )
        return updatedNote
    }

    fun setLineChecked(noteId: String, lineId: String, isChecked: Boolean): NoteDetail? {
        val stamp = nowStamp()
        var updatedNote: NoteDetail? = null
        writeNotes(
            readNotes().map { note ->
                if (note.id == noteId) {
                    note.copy(
                        lines = note.lines.map { line ->
                            if (line.id == lineId) line.copy(isChecked = isChecked, updatedAt = stamp) else line
                        },
                        updatedAt = stamp
                    ).also { updatedNote = it }
                } else {
                    note
                }
            }
        )
        return updatedNote
    }

    fun updateLine(noteId: String, lineId: String, text: String): NoteDetail? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return loadNote(noteId)
        val stamp = nowStamp()
        var updatedNote: NoteDetail? = null
        writeNotes(
            readNotes().map { note ->
                if (note.id == noteId) {
                    note.copy(
                        lines = note.lines.map { line ->
                            if (line.id == lineId) line.copy(text = trimmed, updatedAt = stamp) else line
                        },
                        updatedAt = stamp
                    ).also { updatedNote = it }
                } else {
                    note
                }
            }
        )
        return updatedNote
    }

    fun deleteLine(noteId: String, lineId: String): NoteDetail? {
        val stamp = nowStamp()
        var updatedNote: NoteDetail? = null
        writeNotes(
            readNotes().map { note ->
                if (note.id == noteId) {
                    note.copy(
                        lines = note.lines.filterNot { it.id == lineId },
                        updatedAt = stamp
                    ).also { updatedNote = it }
                } else {
                    note
                }
            }
        )
        return updatedNote
    }

    private fun readNotes(): List<NoteDetail> {
        val file = notesFile(createIfMissing = false) ?: return emptyList()
        val stamp = file.fileStamp()
        noteCache?.takeIf { it.stamp == stamp }?.let { return it.notes }
        val raw = readText(file).orEmpty().ifBlank { return emptyList() }
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val notes = buildList {
            val items = json.optJSONArray("items") ?: JSONArray()
            for (index in 0 until items.length()) {
                val entry = items.optJSONObject(index) ?: continue
                add(
                    NoteDetail(
                        id = entry.optString("id"),
                        title = entry.optString("title"),
                        lines = buildList {
                            val lines = entry.optJSONArray("lines") ?: JSONArray()
                            for (lineIndex in 0 until lines.length()) {
                                val line = lines.optJSONObject(lineIndex) ?: continue
                                add(
                                    NoteLine(
                                        id = line.optString("id"),
                                        text = line.optString("text"),
                                        isChecked = line.optBoolean("isChecked", false),
                                        updatedAt = line.optString("updatedAt").takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        },
                        updatedAt = entry.optString("updatedAt").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        noteCache = NoteCache(stamp, notes)
        return notes
    }

    private fun writeNotes(notes: List<NoteDetail>) {
        val file = notesFile(createIfMissing = true) ?: return
        val root = JSONObject().apply {
            put("version", 1)
            put("type", "NOTES")
            put("items", JSONArray().apply {
                notes.forEach { note ->
                    put(
                        JSONObject().apply {
                            put("id", note.id)
                            put("title", note.title)
                            put("updatedAt", note.updatedAt.orEmpty())
                            put("lines", JSONArray().apply {
                                note.lines.forEach { line ->
                                    put(
                                        JSONObject().apply {
                                            put("id", line.id)
                                            put("text", line.text)
                                            put("isChecked", line.isChecked)
                                            put("updatedAt", line.updatedAt.orEmpty())
                                        }
                                    )
                                }
                            })
                        }
                    )
                }
            })
        }
        contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(root.toString(2))
        }
        noteCache = null
    }

    private fun notesFile(createIfMissing: Boolean): DocumentFile? {
        val folder = resolveFolder() ?: return null
        return folder.findFile(NOTES_FILE_NAME)
            ?: if (createIfMissing) folder.createFile("application/json", NOTES_FILE_NAME) else null
    }

    private fun resolveFolder(): DocumentFile? {
        val folderUri = TranscriptStore.configuredFolderUri(context) ?: return null
        val folderKey = folderUri.toString()
        cachedResolvedFolder?.takeIf {
            cachedFolderKey == folderKey && it.exists() && it.isDirectory
        }?.let { return it }
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        if (!folder.isDirectory) return null
        val resolved = if (folder.name == APP_FOLDER_NAME) {
            folder
        } else {
            folder.findFile(APP_FOLDER_NAME)?.takeIf { it.isDirectory } ?: folder
        }
        cachedFolderKey = folderKey
        cachedResolvedFolder = resolved
        return resolved
    }

    private fun readText(file: DocumentFile): String? {
        return contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
    }

    private fun DocumentFile.fileStamp(): FileStamp? {
        if (!exists()) return null
        return FileStamp(lastModified(), length())
    }

    private fun nowStamp(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private data class FileStamp(
        val lastModified: Long,
        val length: Long
    )

    private data class NoteCache(
        val stamp: FileStamp?,
        val notes: List<NoteDetail>
    )

    private companion object {
        const val APP_FOLDER_NAME = "Face Dragons"
        const val NOTES_FILE_NAME = "app_notes.json"
        const val SOURCE_NAME = "Notes"
    }

    private var cachedFolderKey: String? = null
    private var cachedResolvedFolder: DocumentFile? = null
    private var noteCache: NoteCache? = null
}
