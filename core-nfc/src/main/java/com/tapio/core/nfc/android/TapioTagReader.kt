package com.tapio.core.nfc.android

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.util.Log
import com.tapio.core.nfc.SessionTokenCodec
import com.tapio.core.nfc.apdu.ApduProtocol
import com.tapio.core.nfc.domain.HandshakeError
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.SessionToken
import java.io.IOException

private const val TAG = "TapioReceive"
private const val TIMEOUT_MS = 2_000

/**
 * A cold-launched activity reconnects to a field the platform has just released, so
 * the first `connect()` often lands in a gap and throws "tag lost" while the phones
 * are still touching. Retrying immediately usually catches it.
 */
private const val READ_ATTEMPTS = 3
private const val RETRY_DELAY_MS = 60L

/**
 * Reads a Tapio [SessionToken] from a tapped device running [TapioHostApduService],
 * via the [ApduProtocol] SELECT + READ exchange.
 *
 * `adb logcat -s $TAG TapioHce` shows both sides of the exchange.
 */
object TapioTagReader {

    fun read(tag: Tag): HandshakeOutcome {
        Log.i(TAG, "tag detected: techs=${tag.techList.joinToString()}")
        val isoDep = IsoDep.get(tag) ?: run {
            Log.w(TAG, "tag is not ISO-DEP")
            return HandshakeOutcome.Failure(HandshakeError.MalformedPayload("peer tag is not ISO-DEP"))
        }

        var last: HandshakeOutcome = HandshakeOutcome.Failure(HandshakeError.TagLost)
        repeat(READ_ATTEMPTS) { attempt ->
            last = readOnce(isoDep, attempt + 1)
            if (last is HandshakeOutcome.Success) return last
            // Only a lost field is worth another go; a refusal will refuse again.
            if ((last as HandshakeOutcome.Failure).error !is HandshakeError.TagLost) return last
            Thread.sleep(RETRY_DELAY_MS)
        }
        return last
    }

    @Suppress("SwallowedException")
    private fun readOnce(isoDep: IsoDep, attempt: Int): HandshakeOutcome =
        try {
            if (!isoDep.isConnected) isoDep.connect()
            isoDep.timeout = TIMEOUT_MS
            val token = exchange(isoDep)
            Log.i(TAG, "token read: ${token.deviceName} / ${token.payloadSummary}")
            HandshakeOutcome.Success(token)
        } catch (e: TagLostException) {
            Log.w(TAG, "tag lost during exchange (attempt $attempt)")
            HandshakeOutcome.Failure(HandshakeError.TagLost)
        } catch (e: HandshakeError) {
            Log.w(TAG, "handshake error: ${e.message}")
            HandshakeOutcome.Failure(e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "bad APDU response: ${e.message}")
            HandshakeOutcome.Failure(HandshakeError.MalformedPayload(e.message ?: "invalid APDU response"))
        } catch (e: IOException) {
            Log.w(TAG, "I/O error: ${e.message}")
            HandshakeOutcome.Failure(HandshakeError.Io(e))
        } finally {
            runCatching { isoDep.close() }
        }

    private fun exchange(isoDep: IsoDep): SessionToken {
        val select = ApduProtocol.parseResponse(isoDep.transceive(ApduProtocol.buildSelectApdu()))
        if (!select.isSuccess) throw HandshakeError.MalformedPayload("peer did not accept Tapio AID")

        val read = ApduProtocol.parseResponse(isoDep.transceive(ApduProtocol.buildReadTokenApdu()))
        if (!read.isSuccess) throw HandshakeError.MalformedPayload("peer has no session token staged")

        return SessionTokenCodec.decode(read.payload)
    }
}
