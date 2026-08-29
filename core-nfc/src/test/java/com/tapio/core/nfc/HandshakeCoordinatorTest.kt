package com.tapio.core.nfc

import com.tapio.core.nfc.domain.HandshakeError
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.HandshakeRole
import com.tapio.core.nfc.domain.NfcAvailability
import com.tapio.core.nfc.domain.SessionToken
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class HandshakeCoordinatorTest {

    private val scanner = mockk<NfcTokenScanner>()
    private val coordinator = HandshakeCoordinator(scanner)

    @Test
    fun `emits NfcDisabled failure and never touches the scanner when NFC is off`() = runTest {
        every { scanner.checkAvailability() } returns NfcAvailability.Disabled

        val outcomes = coordinator.awaitPeerToken().toList()

        assertEquals(listOf(HandshakeOutcome.Failure(HandshakeError.NfcDisabled)), outcomes)
        verify(exactly = 0) { scanner.scan() }
    }

    @Test
    fun `emits NfcUnsupported failure when the device has no NFC`() = runTest {
        every { scanner.checkAvailability() } returns NfcAvailability.Unsupported

        val outcomes = coordinator.awaitPeerToken().toList()

        assertEquals(listOf(HandshakeOutcome.Failure(HandshakeError.NfcUnsupported)), outcomes)
    }

    @Test
    fun `forwards scanner outcomes unchanged when NFC is ready`() = runTest {
        val token = SessionToken(
            sessionId = UUID.randomUUID(),
            wifiDirectMac = "02:00:00:00:00:00",
            deviceName = "Peer",
            role = HandshakeRole.SENDER,
            issuedAtEpochMs = 0,
        )
        every { scanner.checkAvailability() } returns NfcAvailability.Ready
        every { scanner.scan() } returns flowOf(HandshakeOutcome.Success(token))

        val outcomes = coordinator.awaitPeerToken().toList()

        assertEquals(listOf(HandshakeOutcome.Success(token)), outcomes)
        verify(exactly = 1) { scanner.scan() }
    }
}
