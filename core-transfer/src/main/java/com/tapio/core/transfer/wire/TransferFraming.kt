package com.tapio.core.transfer.wire

import com.tapio.core.transfer.domain.ContentHeader
import com.tapio.core.transfer.domain.ContentPreview
import com.tapio.core.transfer.domain.TransferError
import java.io.InputStream
import java.io.OutputStream

/**
 * The on-the-wire layout of a whole transfer:
 *
 * ```
 * ┌──────────┬──────────────┐
 * │ int32 len│ preview(len) │  sender → receiver
 * └──────────┴──────────────┘
 *                            ┌─ ─ ─ ─ ─ ─ ┐
 *                            │ decision(1)│  receiver → sender  (accept / decline)
 *                            └─ ─ ─ ─ ─ ─ ┘
 * ┌──────────┬──────────────┬───────────────┬──────────────────┐   (only if accepted)
 * │ int32 len│ header (len) │ bytes (size)  │ SHA-256 tail (32)│  sender → receiver
 * └──────────┴──────────────┴───────────────┴──────────────────┘
 *                            ┌─ ─ ─ ─ ─ ─ ┐
 *                            │  ack (1)   │  receiver → sender  (receipt confirmed)
 *                            └─ ─ ─ ─ ─ ─ ┘
 * ```
 *
 * The preview lets the receiver see a thumbnail + real name/size before deciding;
 * the trailing ack means "sent" on the sender always implies "received & verified".
 */
object TransferFraming {

    /** Guards against a corrupt length prefix causing a huge allocation. */
    const val MAX_HEADER_BYTES: Int = 8 * 1024

    /** A preview carries a small JPEG thumbnail — a bit more headroom than the header. */
    const val MAX_PREVIEW_BYTES: Int = 256 * 1024

    /** Receiver → sender: accept / receipt confirmed. ASCII `ACK`. */
    const val ACK_BYTE: Int = 0x06

    /** Receiver → sender: the preview was declined. ASCII `CAN`. */
    const val DECLINE_BYTE: Int = 0x18

    fun writePreview(out: OutputStream, preview: ContentPreview) {
        val body = PreviewCodec.encode(preview)
        out.writeInt32(body.size)
        out.write(body)
        out.flush()
    }

    /** @throws TransferError.MalformedStream on a bad length prefix or truncated preview. */
    fun readPreview(input: InputStream): ContentPreview {
        val length = input.readInt32()
        if (length !in 1..MAX_PREVIEW_BYTES) {
            throw TransferError.MalformedStream("preview length $length outside 1..$MAX_PREVIEW_BYTES")
        }
        return PreviewCodec.decode(input.readFully(length))
    }

    fun writeDecision(out: OutputStream, accepted: Boolean) {
        out.write(if (accepted) ACK_BYTE else DECLINE_BYTE)
        out.flush()
    }

    /** Sender: block for the receiver's decision. `false` = declined or connection lost. */
    fun readDecision(input: InputStream): Boolean =
        runCatching { input.read() == ACK_BYTE }.getOrDefault(false)

    fun writeHeader(out: OutputStream, header: ContentHeader) {
        val body = ContentHeaderCodec.encode(header)
        out.writeInt32(body.size)
        out.write(body)
    }

    /** @throws TransferError.MalformedStream on a bad length prefix or truncated header. */
    fun readHeader(input: InputStream): ContentHeader {
        val length = input.readInt32()
        if (length !in 1..MAX_HEADER_BYTES) {
            throw TransferError.MalformedStream("header length $length outside 1..$MAX_HEADER_BYTES")
        }
        return ContentHeaderCodec.decode(input.readFully(length))
    }

    fun readChecksumTrailer(input: InputStream): ByteArray = input.readFully(Sha256.SIZE_BYTES)

    /** Receiver → sender: "I have it, and it checks out." */
    fun writeReceiptAck(out: OutputStream) {
        out.write(ACK_BYTE)
        out.flush()
    }

    /** Sender: block until the receiver's ack arrives. `false` on early EOF / anything else. */
    fun readReceiptAck(input: InputStream): Boolean = runCatching { input.read() == ACK_BYTE }.getOrDefault(false)
}
