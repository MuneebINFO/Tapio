package com.tapio.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tapio.core.common.SharedContent

/**
 * Resolves a `content://` [Uri] from the system photo picker into a
 * [SharedContent.File] with its display name, size and MIME type.
 */
fun Uri.toSharedContent(context: Context): SharedContent.File {
    val resolver = context.contentResolver

    var displayName = "fichier"
    var sizeBytes: Long? = null

    resolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { displayName = cursor.getString(it) }
                cursor.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { sizeBytes = cursor.getLong(it) }
            }
        }

    return SharedContent.File(
        uri = toString(),
        displayName = displayName,
        mimeType = resolver.getType(this) ?: "application/octet-stream",
        byteSize = sizeBytes,
    )
}
