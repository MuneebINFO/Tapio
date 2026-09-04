package com.tapio.core.transfer.android

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Size
import com.tapio.core.common.SharedContent
import com.tapio.core.transfer.FileSource
import com.tapio.core.transfer.domain.TransferError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

private const val THUMBNAIL_PX = 400
private const val THUMBNAIL_QUALITY = 55
private const val THUMBNAIL_MAX_BYTES = 200 * 1024

/** [FileSource] that reads outgoing files through the platform `ContentResolver`. */
class ContentResolverFileSource(context: Context) : FileSource {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun sizeOf(content: SharedContent.File): Long {
        content.byteSize?.let { return it }

        val uri = Uri.parse(content.uri)
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                return cursor.getLong(index)
            }
        }
        throw TransferError.MalformedStream("could not determine the size of ${content.displayName}")
    }

    override suspend fun openStream(content: SharedContent.File): InputStream =
        runCatching { resolver.openInputStream(Uri.parse(content.uri)) }
            .getOrNull()
            ?: throw TransferError.Io(IOException("cannot open ${content.uri}"))

    override suspend fun thumbnail(content: SharedContent.File): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (!content.mimeType.startsWith("image/") && !content.mimeType.startsWith("video/")) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = resolver.loadThumbnail(
                    Uri.parse(content.uri),
                    Size(THUMBNAIL_PX, THUMBNAIL_PX),
                    null,
                )
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
                    out.toByteArray()
                }
            }.getOrNull()?.takeIf { it.size in 1..THUMBNAIL_MAX_BYTES }
        }
    }
}
