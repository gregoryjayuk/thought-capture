package com.greggaynor.thoughtcapture.data

import android.content.ContentResolver
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.greggaynor.thoughtcapture.TranscriptStore
import com.greggaynor.thoughtcapture.model.ItemState
import com.greggaynor.thoughtcapture.model.ManagedItem
import com.greggaynor.thoughtcapture.model.ManagedItemType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class KnowledgeRepository(
    private val context: Context,
    private val contentResolver: ContentResolver
) {
    fun loadItems(): List<ManagedItem> {
        val folder = resolveFolder() ?: return emptyList()
        val areaItems = loadAreasBundle(folder).interests
        val editableItems = EDITABLE_FILES.flatMap { definition ->
            val file = folder.findFile(definition.fileName) ?: return@flatMap emptyList()
            readStructuredItems(file, definition.type, editable = definition.editable)
        }
        return (areaItems + editableItems)
            .sortedWith(compareBy({ it.type.displayName }, { it.category ?: "" }, { it.title }))
    }

    fun loadAreasOfFocus(): List<String> {
        val folder = resolveFolder() ?: return emptyList()
        return loadAreasBundle(folder).focus
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }

    fun loadAreaOfFocusItems(): List<ManagedItem> {
        val folder = resolveFolder() ?: return emptyList()
        return loadAreasBundle(folder).focus
            .sortedBy { it.title.lowercase() }
    }

    fun addItem(item: ManagedItem): ManagedItem {
        val saved = item.copy(id = item.id.ifBlank { UUID.randomUUID().toString() })
        if (saved.type == ManagedItemType.AREA_OF_INTEREST || saved.type == ManagedItemType.AREA_OF_FOCUS) {
            val folder = resolveFolder() ?: return saved
            val bundle = loadAreasBundle(folder)
            val updatedBundle = if (saved.type == ManagedItemType.AREA_OF_FOCUS) {
                bundle.copy(focus = bundle.focus + saved.copy(parent = null))
            } else {
                bundle.copy(interests = bundle.interests + saved.copy(parent = null))
            }
            writeAreasBundle(folder, updatedBundle)
            return saved
        }

        val definition = definitionForType(saved.type)
        val items = loadStructuredItems(definition.type).toMutableList()
        items += saved
        writeStructuredItems(definition, items)
        return saved
    }

    fun updateItem(item: ManagedItem) {
        if (item.type == ManagedItemType.AREA_OF_INTEREST || item.type == ManagedItemType.AREA_OF_FOCUS) {
            val folder = resolveFolder() ?: return
            val bundle = loadAreasBundle(folder)
            val updatedBundle = if (item.type == ManagedItemType.AREA_OF_FOCUS) {
                bundle.copy(focus = bundle.focus.map { existing ->
                    if (existing.id == item.id) item.copy(parent = null) else existing
                })
            } else {
                bundle.copy(interests = bundle.interests.map { existing ->
                    if (existing.id == item.id) item.copy(parent = null) else existing
                })
            }
            writeAreasBundle(folder, updatedBundle)
            return
        }

        val definition = definitionForType(item.type)
        val updated = loadStructuredItems(definition.type).map { existing ->
            if (existing.id == item.id) item else existing
        }
        writeStructuredItems(definition, updated)
    }

    fun deleteItem(item: ManagedItem) {
        if (item.type == ManagedItemType.AREA_OF_INTEREST) {
            val folder = resolveFolder() ?: return
            val bundle = loadAreasBundle(folder)
            writeAreasBundle(folder, bundle.copy(interests = bundle.interests.filterNot { it.id == item.id }))
            return
        }

        val definition = definitionForType(item.type)
        val updated = loadStructuredItems(definition.type).filterNot { it.id == item.id }
        writeStructuredItems(definition, updated)
    }

    fun setState(itemId: String, state: ItemState) {
        val folder = resolveFolder() ?: return
        val bundle = loadAreasBundle(folder)
        if (bundle.interests.any { it.id == itemId }) {
            writeAreasBundle(
                folder = folder,
                bundle = bundle.copy(
                    interests = bundle.interests.map { item ->
                        if (item.id == itemId) item.copy(state = state) else item
                    }
                )
            )
            return
        }

        EDITABLE_FILES.forEach { definition ->
            val items = loadStructuredItems(definition.type)
            if (items.any { it.id == itemId }) {
                val updated = items.map { item ->
                    if (item.id == itemId) item.copy(state = state) else item
                }
                writeStructuredItems(definition, updated)
                return
            }
        }
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

    private fun loadStructuredItems(type: ManagedItemType): List<ManagedItem> {
        val definition = definitionForType(type)
        val folder = resolveFolder() ?: return emptyList()
        val file = folder.findFile(definition.fileName) ?: return emptyList()
        return readStructuredItems(file, type, editable = definition.editable)
    }

    private fun loadAreasBundle(folder: DocumentFile): AreasBundle {
        val combinedFile = folder.findFile(AREAS_FILE_NAME)
        val stamp = AreasStamp(combined = combinedFile.fileStamp())
        areasCache?.takeIf { it.stamp == stamp }?.let { return it.bundle }

        val bundle = combinedFile?.let { readAreasFile(it) } ?: AreasBundle()
        areasCache = AreasCache(stamp, bundle)
        return bundle
    }

    private fun readAreasFile(file: DocumentFile): AreasBundle {
        val rawText = readText(file).orEmpty().ifBlank { return AreasBundle() }
        val json = runCatching { JSONObject(rawText) }.getOrNull() ?: return AreasBundle()
        return AreasBundle(
            focus = readItemsArray(
                json.optJSONArray("areasOfFocus") ?: JSONArray(),
                type = ManagedItemType.AREA_OF_FOCUS,
                editable = false
            ),
            interests = readItemsArray(
                json.optJSONArray("areasOfInterest") ?: JSONArray(),
                type = ManagedItemType.AREA_OF_INTEREST,
                editable = true
            )
        )
    }

    private fun writeAreasBundle(folder: DocumentFile, bundle: AreasBundle) {
        val file = folder.findFile(AREAS_FILE_NAME)
            ?: folder.createFile("application/json", AREAS_FILE_NAME)
            ?: return
        val root = JSONObject()
        root.put("version", 1)
        root.put("type", "AREAS")
        root.put("areasOfFocus", JSONArray().apply {
            bundle.focus.forEach { put(it.toJson()) }
        })
        root.put("areasOfInterest", JSONArray().apply {
            bundle.interests.forEach { put(it.toJson()) }
        })

        contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(root.toString(2))
        }
        areasCache = null
    }

    private fun readStructuredItems(
        file: DocumentFile,
        type: ManagedItemType,
        editable: Boolean
    ): List<ManagedItem> {
        val cacheKey = "${type.name}:${file.uri}"
        val fileStamp = file.fileStamp()
        structuredCache[cacheKey]?.takeIf { it.stamp == fileStamp }?.let { return it.items }

        val rawText = readText(file).orEmpty().ifBlank { return emptyList() }
        val json = runCatching { JSONObject(rawText) }.getOrNull() ?: return emptyList()
        val parsedItems = readItemsArray(json.optJSONArray("items") ?: JSONArray(), type, editable)
        structuredCache[cacheKey] = StructuredCache(fileStamp, parsedItems)
        return parsedItems
    }

    private fun readItemsArray(
        items: JSONArray,
        type: ManagedItemType,
        editable: Boolean
    ): List<ManagedItem> {
        return buildList {
            for (index in 0 until items.length()) {
                val entry = items.optJSONObject(index) ?: continue
                add(
                    ManagedItem(
                        id = entry.readNullableString("id").orEmpty(),
                        title = entry.readNullableString("title").orEmpty(),
                        type = type,
                        category = entry.readNullableString("category"),
                        parent = null,
                        state = entry.readState(),
                        imagePath = entry.readNullableString("imagePath"),
                        notes = entry.readNullableString("notes"),
                        dateText = entry.readNullableString("dateText"),
                        isEditable = editable,
                        sourceName = STRUCTURED_SOURCE_NAME
                    )
                )
            }
        }
    }

    private fun writeStructuredItems(definition: StructuredFileDefinition, items: List<ManagedItem>) {
        val folder = resolveFolder() ?: return
        val file = folder.findFile(definition.fileName)
            ?: folder.createFile("application/json", definition.fileName)
            ?: return

        val root = JSONObject()
        root.put("version", 1)
        root.put("type", definition.type.name)
        root.put("items", JSONArray().apply {
            items.forEach { put(it.toJson()) }
        })

        contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(root.toString(2))
        }
        structuredCache.remove("${definition.type.name}:${file.uri}")
    }

    private fun ManagedItem.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            category?.let { put("category", it) }
            put("state", state.name)
            put("isActive", isActive)
            imagePath?.let { put("imagePath", it) }
            notes?.let { put("notes", it) }
            dateText?.let { put("dateText", it) }
        }
    }

    private fun readText(file: DocumentFile): String? {
        return contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
    }

    private fun definitionForType(type: ManagedItemType): StructuredFileDefinition {
        return EDITABLE_FILES.first { it.type == type }
    }

    private fun JSONObject.readNullableString(key: String): String? {
        val value = opt(key)
        if (value == null || value == JSONObject.NULL) return null
        return value.toString()
            .trim()
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun JSONObject.readState(): ItemState {
        val stateValue = readNullableString("state")
        if (stateValue != null) {
            runCatching { ItemState.valueOf(stateValue) }.getOrNull()?.let { return it }
        }
        return if (optBoolean("isActive", true)) ItemState.ACTIVE else ItemState.INACTIVE
    }

    private fun DocumentFile?.fileStamp(): FileStamp? {
        if (this == null || !exists()) return null
        return FileStamp(lastModified(), length())
    }

    private data class StructuredFileDefinition(
        val type: ManagedItemType,
        val fileName: String,
        val editable: Boolean
    )

    private data class FileStamp(
        val lastModified: Long,
        val length: Long
    )

    private data class StructuredCache(
        val stamp: FileStamp?,
        val items: List<ManagedItem>
    )

    private data class AreasBundle(
        val focus: List<ManagedItem> = emptyList(),
        val interests: List<ManagedItem> = emptyList()
    )

    private data class AreasStamp(
        val combined: FileStamp?
    )

    private data class AreasCache(
        val stamp: AreasStamp,
        val bundle: AreasBundle
    )

    private companion object {
        const val STRUCTURED_SOURCE_NAME = "App Data"
        const val APP_FOLDER_NAME = "Face Dragons"

        const val AREAS_FILE_NAME = "app_areas.json"

        val EDITABLE_FILES = listOf(
            StructuredFileDefinition(
                type = ManagedItemType.GOAL,
                fileName = "app_goals.json",
                editable = true
            ),
            StructuredFileDefinition(
                type = ManagedItemType.VISION,
                fileName = "app_vision.json",
                editable = true
            ),
            StructuredFileDefinition(
                type = ManagedItemType.MILESTONE,
                fileName = "app_milestones.json",
                editable = true
            )
        )
    }

    private val structuredCache = mutableMapOf<String, StructuredCache>()
    private var areasCache: AreasCache? = null
    private var cachedFolderKey: String? = null
    private var cachedResolvedFolder: DocumentFile? = null
}
