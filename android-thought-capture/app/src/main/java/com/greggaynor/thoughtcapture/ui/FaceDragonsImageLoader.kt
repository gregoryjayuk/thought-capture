package com.greggaynor.thoughtcapture.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import androidx.documentfile.provider.DocumentFile
import com.greggaynor.thoughtcapture.R
import com.greggaynor.thoughtcapture.TranscriptStore
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors
import kotlin.math.max

object FaceDragonsImageLoader {
    private const val CACHE_VERSION = 3
    private val decodeExecutor = Executors.newFixedThreadPool(2)
    private val bitmapCache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun loadInto(
        imageView: ImageView,
        imagePath: String?,
        targetSizePx: Int,
        onLoaded: (Bitmap?) -> Unit
    ) {
        val normalizedPath = imagePath?.trim().orEmpty()
        val cacheKey = buildCacheKey(normalizedPath, targetSizePx)
        imageView.setTag(R.id.item_image, cacheKey)

        if (normalizedPath.isBlank()) {
            onLoaded(null)
            return
        }

        bitmapCache.get(cacheKey)?.let {
            onLoaded(it)
            return
        }

        onLoaded(null)
        val appContext = imageView.context.applicationContext
        decodeExecutor.execute {
            val bitmap = loadBitmapSync(appContext, normalizedPath, targetSizePx)
            if (bitmap != null) {
                bitmapCache.put(cacheKey, bitmap)
            }
            imageView.post {
                if (imageView.getTag(R.id.item_image) == cacheKey) {
                    onLoaded(bitmap)
                }
            }
        }
    }

    fun loadBitmapSync(
        context: Context,
        imagePath: String?,
        targetSizePx: Int
    ): Bitmap? {
        val normalizedPath = imagePath?.trim().orEmpty()
        if (normalizedPath.isBlank()) return null

        val cacheKey = buildCacheKey(normalizedPath, targetSizePx)
        bitmapCache.get(cacheKey)?.let { return it }

        val decodeTarget = max(targetSizePx * 2, 192)
        val decoded = when {
            normalizedPath.startsWith("content://") || normalizedPath.startsWith("file://") -> {
                decodeUri(context, Uri.parse(normalizedPath), decodeTarget)
            }

            normalizedPath.startsWith("/") -> {
                decodeFile(normalizedPath, decodeTarget)
            }

            else -> {
                val file = resolveRelativeFile(context, normalizedPath) ?: return null
                decodeUri(context, file.uri, decodeTarget)
            }
        } ?: return null

        val normalized = normalizeBitmap(
            bitmap = decoded,
            outputSizePx = max(targetSizePx * 2, 256),
            contentRatio = 0.82f
        )
        bitmapCache.put(cacheKey, normalized)
        return normalized
    }

    private fun buildCacheKey(path: String, targetSizePx: Int): String {
        return "v$CACHE_VERSION:$path@$targetSizePx"
    }

    private fun decodeUri(context: Context, uri: Uri, targetSizePx: Int): Bitmap? {
        return decodeStreamFactory(
            targetSizePx = targetSizePx,
            streamFactory = { context.contentResolver.openInputStream(uri) }
        )
    }

    private fun decodeFile(path: String, targetSizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(File(path).absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetSizePx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(File(path).absolutePath, options)
    }

    private fun decodeStreamFactory(
        targetSizePx: Int,
        streamFactory: () -> InputStream?
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        streamFactory()?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetSizePx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return streamFactory()?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, targetSizePx: Int): Int {
        var inSampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2

        while (halfWidth / inSampleSize >= targetSizePx && halfHeight / inSampleSize >= targetSizePx) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun normalizeBitmap(
        bitmap: Bitmap,
        outputSizePx: Int,
        contentRatio: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return bitmap

        val visibleBounds = findVisibleBounds(bitmap)
        val source = if (visibleBounds != null &&
            (visibleBounds.left != 0 || visibleBounds.top != 0 || visibleBounds.right != width || visibleBounds.bottom != height)
        ) {
            Bitmap.createBitmap(
                bitmap,
                visibleBounds.left,
                visibleBounds.top,
                visibleBounds.width().coerceAtLeast(1),
                visibleBounds.height().coerceAtLeast(1)
            )
        } else {
            bitmap
        }

        val sourceWidth = source.width.toFloat()
        val sourceHeight = source.height.toFloat()
        val longestSide = max(sourceWidth, sourceHeight)
        if (longestSide <= 0f) return source

        val square = Bitmap.createBitmap(outputSizePx, outputSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val targetSide = outputSizePx * contentRatio
        val scale = targetSide / longestSide
        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val left = (outputSizePx - scaledWidth) / 2f
        val top = (outputSizePx - scaledHeight) / 2f
        val targetRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
        canvas.drawBitmap(source, null, targetRect, paint)
        return square
    }

    private fun findVisibleBounds(bitmap: Bitmap): Rect? {
        if (!bitmap.hasAlpha()) return Rect(0, 0, bitmap.width, bitmap.height)

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var left = width
        var right = -1
        var top = height
        var bottom = -1

        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) {
                val alpha = pixels[rowStart + x] ushr 24
                if (alpha > 12) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }

        return if (right < left || bottom < top) {
            null
        } else {
            Rect(left, top, right + 1, bottom + 1)
        }
    }

    private fun resolveRelativeFile(context: Context, relativePath: String): DocumentFile? {
        val folderUri = TranscriptStore.configuredFolderUri(context) ?: return null
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        val root = if (folder.name == APP_FOLDER_NAME) {
            folder
        } else {
            folder.findFile(APP_FOLDER_NAME)?.takeIf { it.isDirectory } ?: folder
        }

        var current: DocumentFile? = root
        relativePath.split('/').filter { it.isNotBlank() }.forEach { segment ->
            current = current?.findFile(segment)
        }
        return current
    }

    private fun cacheSizeKb(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (maxMemoryKb / 12).coerceAtLeast(4 * 1024)
    }

    private const val APP_FOLDER_NAME = "Face Dragons"
}
