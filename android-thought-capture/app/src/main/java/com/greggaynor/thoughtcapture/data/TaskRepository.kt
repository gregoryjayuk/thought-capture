package com.greggaynor.thoughtcapture.data

import android.content.ContentResolver
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.greggaynor.thoughtcapture.TranscriptStore
import com.greggaynor.thoughtcapture.model.TaskItem
import com.greggaynor.thoughtcapture.model.TaskTags
import java.time.LocalDate

class TaskRepository(
    private val context: Context,
    private val contentResolver: ContentResolver
) {
    fun loadTasks(): List<TaskItem> {
        val folder = resolveFolder() ?: return emptyList()
        val taskFile = folder.findFile(TASKS_FILE_NAME)
        val doneFile = folder.findFile(DONE_TASKS_FILE_NAME)
        val taskStamp = taskFile.fileStamp()
        val doneStamp = doneFile.fileStamp()
        taskCache?.takeIf { it.taskStamp == taskStamp && it.doneStamp == doneStamp }?.let { return it.tasks }

        val lines = ensureTaskGuids(readTaskLines(taskFile), doneFile)
        val tasks = buildList {
            lines.forEachIndexed { index, rawLine ->
                parseTask(rawLine, index)?.let { add(it) }
            }
        }
        taskCache = TaskCache(
            taskStamp = taskFile.fileStamp(),
            doneStamp = doneFile.fileStamp(),
            tasks = tasks
        )
        return tasks
    }

    fun loadCompletedTasks(): List<TaskItem> {
        val folder = resolveFolder() ?: return emptyList()
        val doneFile = folder.findFile(DONE_TASKS_FILE_NAME) ?: return emptyList()
        return readDoneTaskLines(doneFile)
            .mapIndexedNotNull { index, rawLine -> parseCompletedTask(rawLine, index) }
    }

    fun addTask(
        title: String,
        contexts: List<String> = emptyList(),
        projects: List<String> = emptyList(),
        areas: List<String> = emptyList(),
        priority: String? = null,
        dueDate: String? = null,
        prefixTokens: List<String> = emptyList()
    ): String {
        val folder = resolveFolder() ?: return ""
        val taskFile = folder.findFile(TASKS_FILE_NAME)
        val doneFile = folder.findFile(DONE_TASKS_FILE_NAME)
        val lines = ensureTaskGuids(readTaskLines(taskFile), doneFile).toMutableList()
        val guid = nextGuid(lines + readDoneTaskLines(doneFile))
        val effectivePrefixTokens = if (prefixTokens.isEmpty()) {
            listOf(LocalDate.now().toString())
        } else {
            prefixTokens.filter { it.isNotBlank() }
        }
        lines += serializeTask(
            TaskItem(
                id = guid,
                lineIndex = lines.size,
                rawLine = "",
                guid = guid,
                prefixTokens = effectivePrefixTokens,
                title = title,
                contexts = contexts.normalizedTags(),
                projects = projects.normalizedTags(),
                areas = areas.normalizedTags(),
                isSomeday = contexts.any { it.isSomedayContext() },
                priority = priority?.uppercase()?.takeIf { it in VALID_PRIORITIES },
                dueDate = dueDate?.takeIf { it.isNotBlank() }
            )
        )
        writeTaskLines(lines)
        invalidateCache()
        return guid
    }

    fun updateTask(task: TaskItem) {
        updateTasks(listOf(task))
    }

    fun updateTasks(tasks: Collection<TaskItem>) {
        if (tasks.isEmpty()) return
        val folder = resolveFolder() ?: return
        val taskFile = folder.findFile(TASKS_FILE_NAME)
        val doneFile = folder.findFile(DONE_TASKS_FILE_NAME)
        val lines = ensureTaskGuids(readTaskLines(taskFile), doneFile).toMutableList()
        val updatesByGuid = tasks.associateBy { it.guid }
        if (updatesByGuid.isEmpty()) return
        var changed = false

        updatesByGuid.values.forEach { task ->
            val lineIndex = findTaskLineIndex(lines, task)
            if (lineIndex !in lines.indices) return@forEach
            lines[lineIndex] = serializeTask(
                task.copy(
                    id = task.guid,
                    lineIndex = lineIndex,
                    guid = task.guid.ifBlank { nextGuid(lines + readDoneTaskLines(doneFile)) },
                    contexts = task.contexts.normalizedTags(),
                    projects = task.projects.normalizedTags(),
                    areas = task.areas.normalizedTags()
                )
            )
            changed = true
        }
        if (!changed) return
        writeTaskLines(lines)
        invalidateCache()
    }

    fun completeTask(task: TaskItem) {
        completeTasks(listOf(task))
    }

    fun completeTasks(tasks: Collection<TaskItem>) {
        if (tasks.isEmpty()) return
        val folder = resolveFolder() ?: return
        val taskFile = folder.findFile(TASKS_FILE_NAME)
        val doneFile = folder.findFile(DONE_TASKS_FILE_NAME)
        val lines = ensureTaskGuids(readTaskLines(taskFile), doneFile).toMutableList()
        val tasksByGuid = tasks.associateBy { it.guid }
        if (tasksByGuid.isEmpty()) return

        val completionDate = LocalDate.now()
        val completedLines = mutableListOf<String>()
        val remainingLines = lines.filterNot { line ->
            val guid = line.trim().split(Regex("\\s+")).firstOrNull()
            val matched = guid?.let { tasksByGuid[it] } != null
            if (matched) {
                completedLines += "x $completionDate ${line.trim()}"
            }
            matched
        }
        if (completedLines.isEmpty()) return

        lines.clear()
        lines += remainingLines
        writeTaskLines(lines)
        appendDoneTasks(completedLines)
        invalidateCache()
    }

    fun deleteTask(task: TaskItem) {
        val folder = resolveFolder() ?: return
        val taskFile = folder.findFile(TASKS_FILE_NAME)
        val doneFile = folder.findFile(DONE_TASKS_FILE_NAME)
        val lines = ensureTaskGuids(readTaskLines(taskFile), doneFile).toMutableList()
        val lineIndex = findTaskLineIndex(lines, task)
        if (lineIndex !in lines.indices) return
        lines.removeAt(lineIndex)
        writeTaskLines(lines)
        invalidateCache()
    }

    private fun parseTask(rawLine: String, lineIndex: Int): TaskItem? {
        val trimmed = rawLine.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("x ")) return null
        if (trimmed.matches(Regex("^[a-z]:\\d+.*", RegexOption.IGNORE_CASE))) return null

        val tokens = trimmed.split(Regex("\\s+")).toMutableList()
        val guid = if (tokens.firstOrNull()?.matches(GUID_REGEX) == true) {
            tokens.removeAt(0)
        } else {
            legacyGuid(lineIndex)
        }

        val prefixTokens = mutableListOf<String>()
        repeat(3) {
            val token = tokens.firstOrNull() ?: return@repeat
            if (looksLikeDateToken(token)) {
                prefixTokens += tokens.removeAt(0)
            }
        }

        val priority = if (tokens.firstOrNull()?.matches(Regex("^\\([A-Z]\\)$")) == true) {
            tokens.removeAt(0).removePrefix("(").removeSuffix(")")
        } else {
            null
        }

        repeat(3) {
            val token = tokens.firstOrNull() ?: return@repeat
            if (looksLikeDateToken(token)) {
                prefixTokens += tokens.removeAt(0)
            }
        }

        val titleParts = mutableListOf<String>()
        val contexts = mutableListOf<String>()
        val projects = mutableListOf<String>()
        val areas = mutableListOf<String>()
        var dueDate: String? = null

        tokens.forEach { token ->
            when {
                token.startsWith("@") && token.length > 1 -> contexts += token.removePrefix("@")
                token.startsWith("+") && token.length > 1 -> projects += token.removePrefix("+")
                token.startsWith("#") && token.length > 1 -> areas += token.removePrefix("#")
                token.startsWith("due:") && token.length > 4 -> dueDate = token.removePrefix("due:")
                else -> titleParts += token
            }
        }

        val title = titleParts.joinToString(" ").trim().ifBlank { trimmed }
        return TaskItem(
            id = guid,
            lineIndex = lineIndex,
            rawLine = rawLine,
            guid = guid,
            prefixTokens = prefixTokens,
            title = title,
            contexts = contexts.distinct(),
            projects = projects.distinct(),
            areas = areas.distinct(),
            isSomeday = contexts.any { it.equals("Someday", ignoreCase = true) || it.equals("notnow", ignoreCase = true) },
            priority = priority,
            dueDate = dueDate,
            completedDate = null
        )
    }

    private fun parseCompletedTask(rawLine: String, lineIndex: Int): TaskItem? {
        val trimmed = rawLine.trim()
        if (!trimmed.startsWith("x ")) return null
        val parts = trimmed.split(Regex("\\s+"), limit = 3)
        val completedDate = parts.getOrNull(1)
        val payload = parts.getOrNull(2).orEmpty()
        if (payload.isBlank()) return null
        return parseTask(payload, lineIndex)?.copy(
            rawLine = rawLine,
            completedDate = completedDate
        )
    }

    private fun serializeTask(task: TaskItem): String {
        val parts = mutableListOf<String>()
        parts += task.guid.ifBlank { nextGuid(readTaskLines() + readDoneTaskLines()) }
        parts += task.prefixTokens.filter { it.isNotBlank() }
        task.priority?.takeIf { it.isNotBlank() }?.let { parts += "(${it.uppercase()})" }
        parts += task.title.trim()
        parts += task.contexts.distinct().map { "@${it.trimStart('@')}" }
        parts += task.projects.distinct().map { "+${it.trimStart('+')}" }
        parts += task.areas.distinct().map { "#${it.trimStart('#')}" }
        task.dueDate?.takeIf { it.isNotBlank() }?.let { parts += "due:$it" }
        return parts.joinToString(" ").trim()
    }

    private fun appendDoneTasks(linesToAppend: List<String>) {
        if (linesToAppend.isEmpty()) return
        val doneFile = resolveFolder()?.findFile(DONE_TASKS_FILE_NAME)
            ?: resolveFolder()?.createFile("text/plain", DONE_TASKS_FILE_NAME)
            ?: return

        val existing = readText(doneFile).orEmpty()
        val updated = buildString {
            append(existing)
            if (existing.isNotEmpty() && !existing.endsWith("\n")) append('\n')
            linesToAppend.forEach { line ->
                append(line)
                append('\n')
            }
        }
        contentResolver.openOutputStream(doneFile.uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(updated)
        }
    }

    private fun readTaskLines(file: DocumentFile? = resolveFolder()?.findFile(TASKS_FILE_NAME)): List<String> {
        if (file == null) return emptyList()
        val rawText = readText(file).orEmpty()
        val rawLines = rawText.split('\n').map { it.trimEnd('\r') }
        val compactedLines = rawLines.filter { it.isNotBlank() }
        if (rawLines.size != compactedLines.size) {
            writeTaskLines(compactedLines)
        }
        return compactedLines
    }

    private fun readDoneTaskLines(file: DocumentFile? = resolveFolder()?.findFile(DONE_TASKS_FILE_NAME)): List<String> {
        if (file == null) return emptyList()
        return readText(file).orEmpty()
            .split('\n')
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
    }

    private fun writeTaskLines(lines: List<String>) {
        val folder = resolveFolder() ?: return
        val file = folder.findFile(TASKS_FILE_NAME)
            ?: folder.createFile("text/plain", TASKS_FILE_NAME)
            ?: return

        contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { writer ->
            val sanitizedLines = lines.map { it.trimEnd('\r') }.filter { it.isNotBlank() }
            writer.write(sanitizedLines.joinToString("\n"))
            if (sanitizedLines.isNotEmpty()) {
                writer.write("\n")
            }
        }
    }

    private fun readText(file: DocumentFile): String? {
        return contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
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

    private fun looksLikeDateToken(token: String): Boolean {
        return token.matches(Regex("^[0-9xX]{1,4}([-/][0-9xX]{1,4}){0,2}$"))
    }

    private fun ensureTaskGuids(lines: List<String>, doneFile: DocumentFile? = resolveFolder()?.findFile(DONE_TASKS_FILE_NAME)): List<String> {
        val needsGuids = lines.any { line ->
            val trimmed = line.trim()
            isActiveTaskLine(trimmed) &&
                trimmed.split(Regex("\\s+")).firstOrNull()?.matches(GUID_REGEX) != true
        }
        if (!needsGuids) return lines

        var nextNumber = highestGuidNumber(lines + readDoneTaskLines(doneFile)) + 1
        val updatedLines = lines.map { line ->
            val trimmed = line.trim()
            if (!isActiveTaskLine(trimmed) || trimmed.split(Regex("\\s+")).firstOrNull()?.matches(GUID_REGEX) == true) {
                line
            } else {
                "${formatGuid(nextNumber++)} $trimmed"
            }
        }
        if (updatedLines != lines) {
            writeTaskLines(updatedLines)
        }
        return updatedLines
    }

    private fun findTaskLineIndex(lines: List<String>, task: TaskItem): Int {
        val byGuid = lines.indexOfFirst { line ->
            line.trim().split(Regex("\\s+")).firstOrNull() == task.guid
        }
        if (byGuid >= 0) return byGuid
        if (task.lineIndex in lines.indices && lines[task.lineIndex].trim() == task.rawLine.trim()) {
            return task.lineIndex
        }
        return lines.indexOfFirst { it.trim() == task.rawLine.trim() }
    }

    private fun isActiveTaskLine(trimmed: String): Boolean {
        if (trimmed.isBlank()) return false
        if (trimmed.startsWith("x ")) return false
        if (trimmed.matches(Regex("^[a-z]:\\d+.*", RegexOption.IGNORE_CASE))) return false
        return true
    }

    private fun highestGuidNumber(lines: List<String>): Int {
        return lines.maxOfOrNull { line ->
            line.trim()
                .split(Regex("\\s+"))
                .firstOrNull { it.matches(GUID_REGEX) }
                ?.let { GUID_REGEX.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
                ?: 0
        } ?: 0
    }

    private fun nextGuid(lines: List<String>): String = formatGuid(highestGuidNumber(lines) + 1)

    private fun formatGuid(number: Int): String = "ID${number.toString().padStart(5, '0')}"

    private fun legacyGuid(lineIndex: Int): String = "LEGACY${lineIndex.toString().padStart(5, '0')}"

    private fun String.isSomedayContext(): Boolean {
        return equals("Someday", ignoreCase = true) || equals("notnow", ignoreCase = true)
    }

    private fun List<String>.normalizedTags(): List<String> {
        return map { TaskTags.toStorageValue(it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    private fun invalidateCache() {
        taskCache = null
    }

    private fun DocumentFile?.fileStamp(): FileStamp? {
        if (this == null || !exists()) return null
        return FileStamp(lastModified(), length())
    }

    private data class FileStamp(
        val lastModified: Long,
        val length: Long
    )

    private data class TaskCache(
        val taskStamp: FileStamp?,
        val doneStamp: FileStamp?,
        val tasks: List<TaskItem>
    )

    private companion object {
        const val APP_FOLDER_NAME = "Face Dragons"
        const val TASKS_FILE_NAME = "tasks"
        const val DONE_TASKS_FILE_NAME = "donetasks"
        val GUID_REGEX = Regex("^ID(\\d{5,})$", RegexOption.IGNORE_CASE)
        val VALID_PRIORITIES = setOf("A", "B", "C")
    }

    @Volatile
    private var taskCache: TaskCache? = null
    @Volatile
    private var cachedFolderKey: String? = null
    @Volatile
    private var cachedResolvedFolder: DocumentFile? = null
}
