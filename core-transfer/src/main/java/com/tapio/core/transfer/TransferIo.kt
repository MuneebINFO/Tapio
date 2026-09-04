package com.tapio.core.transfer

import com.tapio.core.common.SharedContent
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.domain.TransferError
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * A duplex byte pipe between the two phones. In production this wraps a TCP socket
 * over the Wi-Fi Direct group; in tests it is backed by in-memory buffers.
 */
interface TransferChannel : Closeable {
    suspend fun openInput(): InputStream
    suspend fun openOutput(): OutputStream
}

/**
 * Turns the [SessionToken] from the NFC handshake into a live [TransferChannel] by
 * forming (or joining) a Wi-Fi Direct group and opening a socket.
 */
interface WifiDirectConnector {
    /** @throws TransferError if the connection cannot be established. */
    suspend fun connect(token: SessionToken): TransferChannel
}

/** Reads the bytes of an outgoing [SharedContent.File] from the sending device. */
interface FileSource {
    /** @throws TransferError if the size cannot be determined. */
    suspend fun sizeOf(content: SharedContent.File): Long

    /** @throws TransferError if the content cannot be opened. */
    suspend fun openStream(content: SharedContent.File): InputStream

    /** A small JPEG for the pre-accept preview, or `null` if none can be made. */
    suspend fun thumbnail(content: SharedContent.File): ByteArray? = null
}

/**
 * Creates a destination for an incoming file. The file is written to a staging
 * area first; the user's accept/decline choice then [StagedFile.persist]s or
 * [StagedFile.discard]s it.
 */
interface FileSink {
    suspend fun create(header: ContentHeader): StagedFile
}

/** A half-written destination for a received file. */
interface StagedFile : Closeable {

    /** Where the received bytes are written. */
    val output: OutputStream

    /** Promote the staged file to a user-visible location. */
    suspend fun persist(): ReceivedFile

    /** Delete the staged file (user declined, or the transfer failed). */
    suspend fun discard()
}

/** A file that has been accepted by the receiving user and saved for keeps. */
data class ReceivedFile(
    val uri: String,
    val displayName: String,
)
