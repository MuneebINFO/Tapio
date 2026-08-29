package com.tapio.app.data

import com.tapio.core.nfc.NfcTokenAdvertiser
import com.tapio.core.nfc.NfcTokenScanner
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.FileReceiver
import com.tapio.core.transfer.FileSender

/**
 * The seam between the UI/ViewModels and the `core-nfc` / `core-transfer` machinery.
 *
 * Two implementations exist: [FakeTransferBackend] (in-memory, drives both sides of
 * a transfer in-process — used by default so the app is fully explorable on any
 * device) and a real one backed by NFC + Wi-Fi Direct (wired once step 5 lands).
 */
interface TransferBackend {

    val advertiser: NfcTokenAdvertiser
    val scanner: NfcTokenScanner

    fun newSender(): FileSender
    fun newReceiver(): FileReceiver

    /** Sender side: prepare this device's Wi-Fi Direct endpoint and mint the NFC token. */
    suspend fun createLocalToken(): SessionToken

    /** Debug affordances for exploring the flows on a single device; no-ops in production. */
    val demo: DemoControls?
}

/** Single-device demo hooks — fake a second phone. */
interface DemoControls {

    /** From the send screen: pretend a peer tapped and is now pulling the file. */
    fun peerPicksUpFile()

    /** From the receive screen: pretend a peer tapped and is sending us a sample file. */
    fun peerSendsSampleFile()
}
