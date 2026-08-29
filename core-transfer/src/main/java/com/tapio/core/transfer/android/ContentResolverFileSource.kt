package com.tapio.core.transfer.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tapio.core.common.SharedContent
import com.tapio.core.transfer.FileSource
import com.tapio.core.transfer.domain.TransferError
import java.io.IOException
import java.io.InputStream

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
}
