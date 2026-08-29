package com.tapio.core.nfc.android

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import com.tapio.core.nfc.NfcTokenScanner
import com.tapio.core.nfc.SessionTokenCodec
import com.tapio.core.nfc.apdu.ApduProtocol
import com.tapio.core.nfc.domain.HandshakeError
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.NfcAvailability
import com.tapio.core.nfc.domain.SessionToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException

/**
 * [NfcTokenScanner] built on [NfcAdapter.enableReaderMode]. Bound to an [Activity]
 * because reader mode is only active while that activity is resumed.
 *
 * On each tap it performs the [ApduProtocol] exchange against the peer's
 * [TapioHostApduService] and emits a decoded [HandshakeOutcome].
 */
class ReaderModeTokenScanner(private val activity: Activity) : NfcTokenScanner {

    private val availabilityChecker = AndroidNfcAvailabilityChecker(activity)

    override fun checkAvailability(): NfcAvailability = availabilityChecker.current()

    override fun scan(): Flow<HandshakeOutcome> = callbackFlow {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null) {
            trySend(HandshakeOutcome.Failure(HandshakeError.NfcUnsupported))
            close()
            return@callbackFlow
        }
        if (!adapter.isEnabled) {
            trySend(HandshakeOutcome.Failure(HandshakeError.NfcDisabled))
            close()
            return@callbackFlow
        }

        val callback = NfcAdapter.ReaderCallback { tag ->
            trySend(readToken(IsoDep.get(tag)))
        }

        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        adapter.enableReaderMode(activity, callback, flags, null)
        awaitClose { adapter.disableReaderMode(activity) }
    }

    // TagLostException carries no useful detail beyond "phones separated", which
    // HandshakeError.TagLost already conveys; the stack trace is not actionable here.
    @Suppress("SwallowedException")
    private fun readToken(isoDep: IsoDep?): HandshakeOutcome {
        isoDep ?: return HandshakeOutcome.Failure(
            HandshakeError.MalformedPayload("peer tag does not support ISO-DEP"),
        )

        return try {
            isoDep.connect()
            HandshakeOutcome.Success(exchangeToken(isoDep))
        } catch (e: TagLostException) {
            HandshakeOutcome.Failure(HandshakeError.TagLost)
        } catch (e: HandshakeError) {
            HandshakeOutcome.Failure(e)
        } catch (e: IllegalArgumentException) {
            HandshakeOutcome.Failure(HandshakeError.MalformedPayload(e.message ?: "invalid APDU response"))
        } catch (e: IOException) {
            HandshakeOutcome.Failure(HandshakeError.Io(e))
        } finally {
            runCatching { isoDep.close() }
        }
    }

    /**
     * Runs the SELECT + READ exchange against a connected peer.
     *
     * @throws HandshakeError.MalformedPayload if the peer rejects the AID or has no token staged.
     */
    private fun exchangeToken(isoDep: IsoDep): SessionToken {
        val select = ApduProtocol.parseResponse(isoDep.transceive(ApduProtocol.buildSelectApdu()))
        if (!select.isSuccess) throw HandshakeError.MalformedPayload("peer did not accept Tapio AID")

        val read = ApduProtocol.parseResponse(isoDep.transceive(ApduProtocol.buildReadTokenApdu()))
        if (!read.isSuccess) throw HandshakeError.MalformedPayload("peer has no session token staged")

        return SessionTokenCodec.decode(read.payload)
    }
}
