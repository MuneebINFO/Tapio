package com.tapio.app.ui.receive

import com.tapio.app.data.FakeTransferBackend
import com.tapio.app.ui.model.ReceiveUiState
import com.tapio.core.nfc.domain.HandshakeRole
import com.tapio.core.nfc.domain.SessionToken
import com.tapio.core.transfer.TransferConfig
import com.tapio.core.transfer.testing.InMemoryFileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveViewModelTest {

    private val scheduler = TestCoroutineScheduler()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun backend() = FakeTransferBackend(
        fileSource = InMemoryFileSource(emptyMap()),
        transferConfig = TransferConfig(
            dispatcher = Dispatchers.Unconfined,
            progressIntervalMs = 0L,
            clock = { 0L },
        ),
    )

    @Test
    fun `starts out waiting for a tap`() = runTest(scheduler) {
        assertTrue(ReceiveViewModel(backend()).state.value is ReceiveUiState.WaitingForTap)
    }

    @Test
    fun `a tap shows the accept prompt, not the content`() = runTest(scheduler) {
        val backend = backend()
        val viewModel = ReceiveViewModel(backend)

        backend.peerSendsSampleFile()
        advanceUntilIdle()

        assertTrue("actual: ${viewModel.state.value}", viewModel.state.value is ReceiveUiState.AwaitingAcceptance)
    }

    @Test
    fun `an NFC-launched token starts straight at the accept prompt`() = runTest(scheduler) {
        val token = SessionToken(
            sessionId = UUID.randomUUID(),
            wifiDirectMac = "02:00:00:00:00:00",
            deviceName = "Téléphone de Marie",
            payloadSummary = "Une photo",
            role = HandshakeRole.SENDER,
            issuedAtEpochMs = 0L,
        )

        val state = ReceiveViewModel(backend(), incomingToken = token).state.value

        assertEquals(ReceiveUiState.AwaitingAcceptance("Téléphone de Marie", "Une photo"), state)
    }

    @Test
    fun `refusing at the prompt reports Declined without receiving`() = runTest(scheduler) {
        val backend = backend()
        val viewModel = ReceiveViewModel(backend)
        backend.peerSendsSampleFile()
        advanceUntilIdle()

        viewModel.refuse()

        assertEquals(ReceiveUiState.Declined, viewModel.state.value)
    }

    @Test
    fun `accepting a file drives to the save dialog, then Saved`() = runTest(scheduler) {
        val backend = backend()
        val viewModel = ReceiveViewModel(backend)
        backend.peerSendsSampleFile()
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()
        assertTrue("actual: ${viewModel.state.value}", viewModel.state.value is ReceiveUiState.FileArrived)

        viewModel.acceptFile()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is ReceiveUiState.Saved)
    }

    @Test
    fun `accepting a contact ends at the contact dialog`() = runTest(scheduler) {
        val backend = backend()
        val viewModel = ReceiveViewModel(backend)
        backend.peerSharesSampleContact()
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()

        val arrived = viewModel.state.value
        assertTrue("actual: $arrived", arrived is ReceiveUiState.ContactArrived)
        assertEquals("Léa Martin", (arrived as ReceiveUiState.ContactArrived).contact.card.displayName)

        viewModel.onContactHandedOff()
        assertTrue(viewModel.state.value is ReceiveUiState.Saved)
    }

    @Test
    fun `declining the arrived file reports Declined`() = runTest(scheduler) {
        val backend = backend()
        val viewModel = ReceiveViewModel(backend)
        backend.peerSendsSampleFile()
        advanceUntilIdle()
        viewModel.accept()
        advanceUntilIdle()

        viewModel.declineArrival()
        advanceUntilIdle()

        assertEquals(ReceiveUiState.Declined, viewModel.state.value)
    }
}
