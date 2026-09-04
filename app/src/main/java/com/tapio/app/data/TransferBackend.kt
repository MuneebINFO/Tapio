package com.tapio.app.data

import com.tapio.core.nfc.NfcTokenAdvertiser
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.FileReceiver
import com.tapio.core.transfer.FileSender
import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between the UI/ViewModels and the `core-nfc` / `core-transfer` machinery.
 *
 * There is no "scanner": a phone receives by being **tapped** — the NFC tag the
 * sender emulates launches Tapio through the manifest intent filter, even when the
 * app is closed. The token then arrives as an `Intent`, not a scan.
 */
interface TransferBackend {

    /** Sender side: keeps the handshake token live over NFC while "hold the phones together" shows. */
    val advertiser: NfcTokenAdvertiser

    fun newSender(): FileSender
    fun newReceiver(): FileReceiver

    /**
     * Sender side: prepare this device's Wi-Fi Direct endpoint and mint the NFC
     * token, embedding [payloadSummary] so the receiver can decide before receiving.
     */
    suspend fun createLocalToken(payloadSummary: String): SessionToken

    /**
     * Sender side: releases everything the current share holds — the Wi-Fi Direct
     * group above all. Called on every outcome (sent, declined, failed, abandoned),
     * because only a fully closed session lets the next one start.
     */
    suspend fun endSession() = Unit

    /** Single-device demo hooks; `null` on a real device. */
    val demo: DemoControls?
}

/** Fakes the "other phone" so the flows can be tried on one device (emulator / no Wi-Fi Direct). */
interface DemoControls {

    /** Send screen: pretend a peer tapped and is now pulling the content. */
    fun peerPicksUpContent()

    /** A pretend incoming tap; `MainActivity` routes it to the accept prompt. */
    val incomingToken: StateFlow<SessionToken?>

    fun simulateIncomingFile()

    fun simulateIncomingContact()

    fun clearIncoming()
}
