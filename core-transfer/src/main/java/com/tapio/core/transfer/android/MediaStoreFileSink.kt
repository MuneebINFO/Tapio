package com.tapio.core.transfer.android

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.tapio.core.transfer.FileSink
import com.tapio.core.transfer.ReceivedFile
import com.tapio.core.transfer.StagedFile
import com.tapio.core.transfer.domain.FileHeader
import com.tapio.core.transfer.domain.TransferError
import java.io.IOException
import java.io.OutputStream

/**
 * [FileSink] that stages incoming files in the MediaStore with `IS_PENDING = 1`
 * (Android 10+), only clearing the flag once the user accepts. On failure or
 * decline the pending row is deleted, so nothing half-written is ever visible.
 */
class MediaStoreFileSink(context: Context) : FileSink {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun create(header: FileHeader): StagedFile {
        val collection = collectionFor(header.mimeType)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, header.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, header.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, values)
            ?: throw TransferError.Io(IOException("MediaStore rejected ${header.displayName}"))
        val output = resolver.openOutputStream(uri)
            ?: throw TransferError.Io(IOException("cannot open an output stream for $uri"))

        return MediaStoreStagedFile(resolver, uri, output, header.displayName)
    }

    private fun collectionFor(mimeType: String): Uri = when {
        mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        else -> throw TransferError.MalformedStream("unsupported file type on this Android version: $mimeType")
    }

    private class MediaStoreStagedFile(
        private val resolver: ContentResolver,
        private val uri: Uri,
        override val output: OutputStream,
        private val displayName: String,
    ) : StagedFile {

        override fun close() {
            runCatching { output.close() }
        }

        override suspend fun persist(): ReceivedFile {
            close()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            return ReceivedFile(uri.toString(), displayName)
        }

        override suspend fun discard() {
            close()
            runCatching { resolver.delete(uri, null, null) }
        }
    }
}
