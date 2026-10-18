package com.greggaynor.thoughtcapture.data

import android.content.ContentResolver
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.greggaynor.thoughtcapture.TranscriptStore
import com.greggaynor.thoughtcapture.model.ManagedItem
import com.greggaynor.thoughtcapture.model.ManagedItemType
import com.greggaynor.thoughtcapture.model.ItemState
import com.greggaynor.thoughtcapture.model.TaskItem
import com.greggaynor.thoughtcapture.model.TaskTags
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ProjectRepository(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val taskRepository: TaskRepository
) {
    private val activationStateStore = ActivationStateStore(context)

    fun loadProjects(tasks: List<TaskItem>, includeMetadata: Boolean = true): List<ManagedItem> {
        val projectFiles = if (includeMetadata) projectFilesById() else emptyMap()
        return tasks
            .filter { it.isProjectShell() }
            .map { shell ->
                val metadata = if (includeMetadata) {
                    readProjectFile(
                        projectId = shell.guid,
                        includeNotes = false,
                        fileOverride = projectFiles[shell.guid]
                    )
                } else {
                    ProjectFileData(id = shell.guid)
                }
                val slug = shell.projects.firstOrNull() ?: TaskTags.toStorageValue(shell.title)
                val shellType = shell.contexts.firstOrNull { it.isProjectShellContext() } ?: DEFAULT_SHELL_CONTEXT
                val relatedTaskCount = tasks.count { task ->
                    task.guid != shell.guid &&
                        !task.isProjectShell() &&
                        task.projects.any { it.equals(slug, ignoreCase = true) }
                }
                ManagedItem(
                    id = shell.guid,
                    title = metadata.title ?: shell.title,
                    type = ManagedItemType.PROJECT,
                    category = normalizeAreaOfFocus(metadata.category, shellType),
                    parent = listOfNotNull(
                        displayShellType(shellType),
                        relatedTaskCount.takeIf { it > 0 }?.let { "$it related tasks" }
                    ).joinToString("  •  ").ifBlank { null },
                    state = activationStateStore.state(shell.guid, metadata.state),
                    imagePath = metadata.imagePath?.takeIf { includeMetadata },
                    notes = metadata.why?.takeIf { includeMetadata },
                    dateText = metadata.dueDate?.takeIf { includeMetadata },
                    isEditable = true,
                    sourceName = TASKS_SOURCE_NAME
                )
            }
            .sortedWith(compareBy({ it.category ?: "" }, { it.title.lowercase() }))
    }

    fun loadProjectDetail(projectId: String, tasks: List<TaskItem> = taskRepository.loadTasks()): ProjectDetail? {
        val shell = tasks.firstOrNull { it.guid == projectId && it.isProjectShell() } ?: return null
        val metadata = readProjectFile(projectId)
        val slug = metadata.slug ?: shell.projects.firstOrNull() ?: TaskTags.toStorageValue(shell.title)
        val title = metadata.title ?: shell.title
        val shellType = shell.contexts.firstOrNull { it.isProjectShellContext() } ?: DEFAULT_SHELL_CONTEXT
        val areaOfFocus = normalizeAreaOfFocus(metadata.category, shellType)
        val relatedTasks = tasks.filter { task ->
            task.guid != projectId &&
                !task.isProjectShell() &&
                task.projects.any { it.equals(slug, ignoreCase = true) }
        }
        val notesBody = metadata.notes.trim()
        val notesSummary = notesBody.lineSequence().firstOrNull { it.isNotBlank() }

        return ProjectDetail(
            item = ManagedItem(
                id = projectId,
                title = title,
                type = ManagedItemType.PROJECT,
                category = areaOfFocus,
                parent = listOfNotNull(
                    displayShellType(shellType),
                    relatedTasks.size.takeIf { it > 0 }?.let { "$it related tasks" }
                ).joinToString("  •  ").ifBlank { null },
                state = activationStateStore.state(projectId, metadata.state),
                imagePath = metadata.imagePath,
                notes = notesSummary,
                dateText = metadata.dueDate,
                isEditable = true,
                sourceName = TASKS_SOURCE_NAME
            ),
            slug = slug,
            shellType = shellType,
            whyBody = metadata.why?.trim().orEmpty(),
            notesBody = notesBody,
            dueDate = metadata.dueDate,
            relatedTasks = relatedTasks
        )
    }

    fun createProject(
        title: String,
        shellType: String,
        areaOfFocus: String?,
        imagePath: String?,
        whyBody: String?,
        notesBody: String?,
        dueDate: String?,
        state: ItemState = ItemState.ACTIVE
    ): ManagedItem {
        val slug = TaskTags.toStorageValue(title)
        val guid = taskRepository.addTask(
            title = title,
            contexts = listOf(shellType),
            projects = listOf(slug)
        )
        writeProjectFile(
            ProjectFileData(
                id = guid,
                title = title,
                slug = slug,
                category = areaOfFocus,
                state = state,
                imagePath = imagePath,
                why = whyBody,
                dueDate = dueDate,
                notes = notesBody.orEmpty().trim()
            )
        )
        activationStateStore.setState(guid, state)
        return loadProjectDetail(guid, taskRepository.loadTasks())!!.item
    }

    fun completeProject(projectId: String) {
        val tasks = taskRepository.loadTasks()
        val detail = loadProjectDetail(projectId, tasks) ?: return
        val relatedTasks = tasks.filter { task ->
            task.guid == projectId ||
                task.projects.any { it.equals(detail.slug, ignoreCase = true) }
        }
        taskRepository.completeTasks(relatedTasks)
        activationStateStore.setState(projectId, ItemState.INACTIVE)
        moveProjectFileToCompleted(detail)
    }

    fun updateProject(
        projectId: String,
        title: String,
        shellType: String,
        areaOfFocus: String?,
        imagePath: String?,
        whyBody: String?,
        notesBody: String?,
        dueDate: String?,
        state: ItemState
    ) {
        val tasks = taskRepository.loadTasks()
        val shell = tasks.firstOrNull { it.guid == projectId && it.isProjectShell() } ?: return
        val existing = loadProjectDetail(projectId, tasks)
        val slug = existing?.slug ?: shell.projects.firstOrNull() ?: TaskTags.toStorageValue(shell.title)
        val updatedContexts = (shell.contexts.filterNot { it.isProjectShellContext() } + shellType)
            .distinctBy { it.lowercase() }
        val updatedProjects = (shell.projects + slug)
            .map { TaskTags.toStorageValue(it) }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        taskRepository.updateTask(
            shell.copy(
                title = title,
                contexts = updatedContexts,
                projects = updatedProjects,
                isSomeday = updatedContexts.any { it.equals("Someday", ignoreCase = true) || it.equals("notnow", ignoreCase = true) }
            )
        )

        writeProjectFile(
            ProjectFileData(
                id = projectId,
                title = title,
                slug = slug,
                category = areaOfFocus,
                state = state,
                imagePath = imagePath,
                why = whyBody,
                dueDate = dueDate,
                notes = notesBody.orEmpty().trim()
            )
        )
        activationStateStore.setState(projectId, state)
    }

    fun deleteProject(projectId: String) {
        taskRepository.loadTasks()
            .firstOrNull { it.guid == projectId && it.isProjectShell() }
            ?.let { taskRepository.deleteTask(it) }
        headerCache.remove(projectId)
        projectFile(projectId)?.delete()
        completedProjectFile(projectId)?.delete()
    }

    fun setState(projectId: String, state: ItemState) {
        val detail = loadProjectDetail(projectId) ?: return
        activationStateStore.setState(projectId, state)
        writeProjectFile(
            ProjectFileData(
                id = projectId,
                title = detail.item.title,
                slug = detail.slug,
                category = detail.item.category,
                state = state,
                imagePath = detail.item.imagePath,
                why = detail.whyBody,
                dueDate = detail.dueDate,
                notes = detail.notesBody
            )
        )
    }

    fun appendVoiceNote(projectId: String, text: String) {
        appendNote(projectId, text, includeTimestamp = true)
    }

    fun appendNote(projectId: String, text: String, includeTimestamp: Boolean = false) {
        val detail = loadProjectDetail(projectId) ?: return
        val appendedNotes = buildString {
            if (detail.notesBody.isNotBlank()) {
                append(detail.notesBody.trimEnd())
                append("\n\n")
            }
            if (includeTimestamp) {
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                append("[")
                append(timestamp)
                append("]\n")
            }
            append(text.trim())
        }
        writeProjectFile(
            ProjectFileData(
                id = projectId,
                title = detail.item.title,
                slug = detail.slug,
                category = detail.item.category,
                state = detail.item.state,
                imagePath = detail.item.imagePath,
                why = detail.whyBody,
                dueDate = detail.dueDate,
                notes = appendedNotes
            )
        )
    }

    private fun readProjectFile(
        projectId: String,
        includeNotes: Boolean = true,
        fileOverride: DocumentFile? = null
    ): ProjectFileData {
        val file = fileOverride ?: projectFile(projectId) ?: return ProjectFileData(id = projectId)
        val stamp = file.fileStamp()
        if (!includeNotes) {
            headerCache[projectId]?.takeIf { it.stamp == stamp }?.let { return it.data }
        }
        val raw = readText(file).orEmpty()
        if (raw.isBlank()) return ProjectFileData(id = projectId)

        val separatorIndex = raw.indexOf("\n---\n").takeIf { it >= 0 } ?: raw.indexOf("\r\n---\r\n")
        val headerText: String
        val notesText: String
        if (separatorIndex >= 0) {
            headerText = raw.substring(0, separatorIndex)
            notesText = if (includeNotes) {
                raw.substring(separatorIndex).substringAfter("---").trimStart('\r', '\n')
            } else {
                ""
            }
        } else {
            headerText = raw
            notesText = ""
        }

        val values = headerText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val delimiter = line.indexOf(':')
                if (delimiter <= 0) return@mapNotNull null
                line.substring(0, delimiter).trim() to line.substring(delimiter + 1).trim()
            }
            .toMap()

        val parsed = ProjectFileData(
            id = values["id"] ?: projectId,
            title = values["title"],
            slug = values["slug"],
            category = values["category"],
            state = values["state"]?.let { value ->
                runCatching { ItemState.valueOf(value) }.getOrNull()
            } ?: ItemState.ACTIVE,
            imagePath = values["imagePath"]?.takeIf { it.isNotBlank() },
            why = values["why"]?.let(::decodeHeaderValue)?.takeIf { it.isNotBlank() },
            dueDate = values["dueDate"]?.takeIf { it.isNotBlank() },
            notes = notesText.trim()
        )
        if (!includeNotes) {
            headerCache[projectId] = HeaderCache(stamp, parsed.copy(notes = ""))
        }
        return parsed
    }

    private fun writeProjectFile(data: ProjectFileData) {
        headerCache.remove(data.id)
        val file = projectFile(
            projectId = data.id,
            title = data.title,
            createIfMissing = true
        ) ?: return
        writeProjectText(file, data)
    }

    private fun writeProjectText(file: DocumentFile, data: ProjectFileData) {
        val updatedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val text = buildString {
            appendLine("id: ${data.id}")
            appendLine("title: ${data.title.orEmpty()}")
            appendLine("slug: ${data.slug.orEmpty()}")
            appendLine("category: ${data.category.orEmpty()}")
            appendLine("state: ${data.state.name}")
            appendLine("imagePath: ${data.imagePath.orEmpty()}")
            appendLine("why: ${encodeHeaderValue(data.why)}")
            appendLine("dueDate: ${data.dueDate.orEmpty()}")
            appendLine("updatedAt: $updatedAt")
            appendLine("---")
            val notes = data.notes.trim()
            if (notes.isNotBlank()) {
                append(notes)
                appendLine()
            }
        }
        contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(text)
        }
    }

    private fun moveProjectFileToCompleted(detail: ProjectDetail) {
        val destinationFolder = completedProjectsFolder(createIfMissing = true) ?: return
        val destinationName = projectFileName(detail.item.id, detail.item.title)
        val destination = destinationFolder.findFile(destinationName)
            ?: destinationFolder.createFile("text/markdown", destinationName.removeSuffix(".md"))
            ?: return
        writeProjectText(
            file = destination,
            data = ProjectFileData(
                id = detail.item.id,
                title = detail.item.title,
                slug = detail.slug,
                category = detail.item.category,
                state = ItemState.INACTIVE,
                imagePath = detail.item.imagePath,
                why = detail.whyBody,
                dueDate = detail.dueDate,
                notes = detail.notesBody
            )
        )
        projectFile(detail.item.id, title = detail.item.title)?.delete()
    }

    private fun projectFile(
        projectId: String,
        title: String? = null,
        createIfMissing: Boolean = false
    ): DocumentFile? {
        return locateProjectFile(
            folder = projectsFolder(createIfMissing),
            projectId = projectId,
            title = title,
            createIfMissing = createIfMissing
        )
    }

    private fun completedProjectFile(projectId: String, title: String? = null): DocumentFile? {
        return locateProjectFile(
            folder = completedProjectsFolder(createIfMissing = false),
            projectId = projectId,
            title = title,
            createIfMissing = false
        )
    }

    private fun locateProjectFile(
        folder: DocumentFile?,
        projectId: String,
        title: String?,
        createIfMissing: Boolean
    ): DocumentFile? {
        folder ?: return null
        val desiredName = projectFileName(projectId, title)
        val candidates = folder.listFiles().filter { file ->
            file.name == "$projectId.md" ||
                file.name.equals(desiredName, ignoreCase = true) ||
                file.name?.startsWith("$projectId ", ignoreCase = true) == true
        }
        val existing = candidates.firstOrNull()
        if (existing != null) {
            val existingName = existing.name.orEmpty()
            if (desiredName.isNotBlank() && existingName != desiredName && !existingName.equals("$projectId.md", ignoreCase = true)) {
                existing.renameTo(desiredName)
            } else if (existingName.equals("$projectId.md", ignoreCase = true) && desiredName.isNotBlank()) {
                existing.renameTo(desiredName)
            }
            return existing
        }
        return if (createIfMissing) {
            folder.createFile("text/markdown", desiredName.removeSuffix(".md"))
        } else {
            null
        }
    }

    private fun projectsFolder(createIfMissing: Boolean): DocumentFile? {
        val root = resolveFolder() ?: return null
        return root.findFile(PROJECTS_FOLDER_NAME)
            ?: if (createIfMissing) root.createDirectory(PROJECTS_FOLDER_NAME) else null
    }

    private fun completedProjectsFolder(createIfMissing: Boolean): DocumentFile? {
        val projectsFolder = projectsFolder(createIfMissing = createIfMissing) ?: return null
        return projectsFolder.findFile(COMPLETED_PROJECTS_FOLDER_NAME)
            ?: if (createIfMissing) projectsFolder.createDirectory(COMPLETED_PROJECTS_FOLDER_NAME) else null
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

    private fun projectFilesById(): Map<String, DocumentFile> {
        val folder = projectsFolder(createIfMissing = false) ?: return emptyMap()
        return folder.listFiles()
            .filter { it.isFile }
            .mapNotNull { file ->
                val name = file.name.orEmpty()
                val id = name.substringBefore(' ').substringBefore(".md").trim()
                    .takeIf { it.startsWith("ID", ignoreCase = true) }
                id?.let { it to file }
            }
            .toMap()
    }

    private fun DocumentFile?.fileStamp(): FileStamp? {
        if (this == null || !exists()) return null
        return FileStamp(lastModified(), length())
    }

    private fun projectFileName(projectId: String, title: String?): String {
        val slug = sanitizeFileSlug(title)
        return if (slug.isBlank()) {
            "$projectId.md"
        } else {
            "$projectId $slug.md"
        }
    }

    private fun sanitizeFileSlug(title: String?): String {
        return title.orEmpty()
            .trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    private fun encodeHeaderValue(value: String?): String {
        return value.orEmpty()
            .trim()
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
    }

    private fun decodeHeaderValue(value: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (current == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    'n' -> {
                        result.append('\n')
                        index += 2
                        continue
                    }
                    '\\' -> {
                        result.append('\\')
                        index += 2
                        continue
                    }
                }
            }
            result.append(current)
            index++
        }
        return result.toString()
    }

    private fun displayShellType(shellContext: String?): String {
        return when (shellContext.orEmpty().trim().lowercase()) {
            "workproject" -> "Work Project"
            else -> "Projects"
        }
    }

    private fun normalizeAreaOfFocus(rawValue: String?, shellType: String): String? {
        val value = rawValue?.trim().orEmpty()
        if (value.isBlank()) return null
        val normalized = value.lowercase()
        return if (
            normalized == shellType.lowercase() ||
            normalized == displayShellType(shellType).lowercase() ||
            normalized in LEGACY_SHELL_CATEGORY_VALUES
        ) {
            null
        } else {
            value
        }
    }

    private fun TaskItem.isProjectShell(): Boolean {
        return contexts.any { it.isProjectShellContext() }
    }

    private fun String.isProjectShellContext(): Boolean {
        return equals("Project", ignoreCase = true) ||
            equals("workproject", ignoreCase = true)
    }

    data class ProjectDetail(
        val item: ManagedItem,
        val slug: String,
        val shellType: String,
        val whyBody: String,
        val notesBody: String,
        val dueDate: String?,
        val relatedTasks: List<TaskItem>
    )

    private data class ProjectFileData(
        val id: String,
        val title: String? = null,
        val slug: String? = null,
        val category: String? = null,
        val state: ItemState = ItemState.ACTIVE,
        val imagePath: String? = null,
        val why: String? = null,
        val dueDate: String? = null,
        val notes: String = ""
    )

    private data class FileStamp(
        val lastModified: Long,
        val length: Long
    )

    private data class HeaderCache(
        val stamp: FileStamp?,
        val data: ProjectFileData
    )

    private companion object {
        const val APP_FOLDER_NAME = "Face Dragons"
        const val PROJECTS_FOLDER_NAME = "projects"
        const val COMPLETED_PROJECTS_FOLDER_NAME = "completed"
        const val TASKS_SOURCE_NAME = "Tasks"
        const val DEFAULT_SHELL_CONTEXT = "Project"
        val LEGACY_SHELL_CATEGORY_VALUES = setOf(
            "project",
            "projects",
            "workproject",
            "work project"
        )
    }

    private var cachedFolderKey: String? = null
    private var cachedResolvedFolder: DocumentFile? = null
    private val headerCache = mutableMapOf<String, HeaderCache>()
}
