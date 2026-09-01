package com.tapio.core.transfer

import com.tapio.core.common.ContentKind
import com.tapio.core.common.SharedContent
import com.tapio.core.nfc.domain.HandshakeRole
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.wire.Sha256
import com.tapio.core.transfer.wire.TransferFraming
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Shared helpers for the `core-transfer` orchestration tests. */
internal object TransferFixtures {

    /** Deterministic config: no dispatcher hop, progress emitted every chunk. */
    fun config(chunkSizeBytes: Int = 4096) = TransferConfig(
        chunkSizeBytes = chunkSizeBytes,
        progressIntervalMs = 0L,
        clock = { 0L },
        dispatcher = Dispatchers.Unconfined,
    )

    fun token() = SessionToken(
        sessionId = UUID.fromString("00000000-0000-0000-0000-0000000000ab"),
        wifiDirectMac = "02:00:00:00:00:00",
        deviceName = "Peer",
        payloadSummary = "Un fichier",
        role = HandshakeRole.RECEIVER,
        issuedAtEpochMs = 0L,
    )

    fun fileContent(uri: String, size: Long, name: String = "photo.jpg", mime: String = "image/jpeg") =
        SharedContent.File(uri = uri, displayName = name, mimeType = mime, byteSize = size)

    fun contactContent(name: String = "Jean Dupont", number: String = "+33 6 12 34 56 78") =
        SharedContent.ContactCard(displayName = name, phoneNumber = number)

    /** Builds the exact bytes a sender would put on the wire for [payload]. */
    fun wireBytes(
        name: String,
        mime: String,
        payload: ByteArray,
        kind: ContentKind = ContentKind.FILE,
        trailer: ByteArray = Sha256.of(payload).bytes,
    ): ByteArray = ByteArrayOutputStream().apply {
        TransferFraming.writeHeader(this, ContentHeader(kind, name, mime, payload.size.toLong()))
        write(payload)
        write(trailer)
    }.toByteArray()
}
