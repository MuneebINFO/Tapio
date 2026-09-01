package com.tapio.app.data

import com.tapio.core.nfc.NfcTokenAdvertiser
import com.tapio.core.nfc.NfcTokenScanner
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.FileReceiver
import com.tapio.core.transfer.FileSender

/**
 * The seam between the UI/ViewModels and the `core-nfc` / `core-transfer` machinery.
 *
 * [FakeTransferBackend] (in-memory, drives both sides of a transfer in-process)
 * powers the app today; a real NFC + Wi-Fi Direct backend gets wired once step 5
 * lands.
 */
interface TransferBackend {

    val advertiser: NfcTokenAdvertiser
    val scanner: NfcTokenScanner

    fun newSender(): FileSender
    fun newReceiver(): FileReceiver

    /**
     * Sender side: prepare this device's Wi-Fi Direct endpoint and mint the NFC
     * token, embedding [payloadSummary] so the receiver can decide before receiving.
     */
    suspend fun createLocalToken(payloadSummary: String): SessionToken

    /** Debug affordances for exploring the flows on a single device; no-ops in production. */
    val demo: DemoControls?
}

/** Single-device demo hooks — fake a second phone. */
interface DemoControls {

    /** From the send screen: pretend a peer tapped and is now pulling the content. */
    fun peerPicksUpContent()

    /** From the receive screen: pretend a peer tapped and is sending us a sample photo. */
    fun peerSendsSampleFile()

    /** From the receive screen: pretend a peer is sharing a contact card with us. */
    fun peerSharesSampleContact()
}
