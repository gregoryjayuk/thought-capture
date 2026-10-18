package com.greggaynor.thoughtcapture

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TranscriptStore {
    private const val PREFS = "transcript_store"
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val FALLBACK_FILE_NAME = "thoughts.txt"
    private const val APP_FOLDER_NAME = "Face Dragons"
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun transcriptFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, FALLBACK_FILE_NAME)
    }

    fun appendThought(context: Context, text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return

        val line = "[${LocalDateTime.now().format(timestampFormatter)}] $cleaned\n"
        appendToConfiguredLocation(context, line)
    }

    fun saveFolderUri(context: Context, folderUri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FOLDER_URI, folderUri.toString())
            .apply()
    }

    fun configuredFolderUri(context: Context): Uri? {
        val uriString = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null)
        return uriString?.let(Uri::parse)
    }

    fun configuredTranscriptPath(context: Context): String {
        val folderUri = configuredFolderUri(context) ?: return transcriptFile(context).absolutePath
        val resolvedUri = resolvedFolderUri(context, folderUri) ?: folderUri
        return buildFriendlyPath(resolvedUri)
    }

    private fun appendToConfiguredLocation(context: Context, line: String) {
        val folderUri = configuredFolderUri(context)
        if (folderUri == null) {
            transcriptFile(context).appendText(line)
            return
        }

        val folder = resolvedFolder(context, folderUri)
        val targetFile = folder
            ?.takeIf { it.exists() && it.isDirectory }
            ?.findFile(FALLBACK_FILE_NAME)
            ?: folder?.createFile("text/plain", FALLBACK_FILE_NAME)

        if (targetFile == null) {
            transcriptFile(context).appendText(line)
            return
        }

        appendViaContentResolver(context.contentResolver, targetFile.uri, line)
    }

    private fun appendViaContentResolver(
        contentResolver: ContentResolver,
        fileUri: Uri,
        line: String
    ) {
        val output = contentResolver.openOutputStream(fileUri, "wa")
            ?: throw IOException("Unable to open transcript file for append")
        output.bufferedWriter().use { writer ->
            writer.append(line)
        }
    }

    private fun buildFriendlyPath(folderUri: Uri): String {
        return try {
            val treeId = DocumentsContract.getTreeDocumentId(folderUri)
            val parts = treeId.split(":", limit = 2)
            if (parts.size == 2 && parts[0] == "primary") {
                "/storage/emulated/0/${parts[1].trimEnd('/')}/$FALLBACK_FILE_NAME"
            } else {
                "$folderUri/$FALLBACK_FILE_NAME"
            }
        } catch (_: Exception) {
            "$folderUri/$FALLBACK_FILE_NAME"
        }
    }

    private fun resolvedFolder(context: Context, folderUri: Uri): DocumentFile? {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        if (!folder.isDirectory) return null
        if (folder.name == APP_FOLDER_NAME) return folder
        return folder.findFile(APP_FOLDER_NAME)?.takeIf { it.isDirectory } ?: folder
    }

    private fun resolvedFolderUri(context: Context, folderUri: Uri): Uri? {
        return resolvedFolder(context, folderUri)?.uri
    }
}
