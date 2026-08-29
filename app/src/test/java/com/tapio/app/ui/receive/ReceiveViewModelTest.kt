package com.tapio.app.ui.receive

import com.tapio.app.data.FakeTransferBackend
import com.tapio.app.ui.model.ReceiveUiState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        val viewModel = ReceiveViewModel(backend())

        assertTrue(viewModel.state.value is ReceiveUiState.WaitingForTap)
    }

    @Test
    fun `a simulated incoming send ends at the save dialog`() = runTest(scheduler) {
        val backend = backend()
        val viewModel = ReceiveViewModel(backend)

        backend.demo.peerSendsSampleFile()
        advanceUntilIdle()

        assertTrue("actual: ${viewModel.state.value}", viewModel.state.value is ReceiveUiState.Arrived)
    }

    @Test
    fun `accepting the file persists it and reports Saved`() = runTest(scheduler) {
        val backend = backend()
        val viewModel = ReceiveViewModel(backend)
        backend.demo.peerSendsSampleFile()
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertTrue("actual: ${viewModel.state.value}", viewModel.state.value is ReceiveUiState.Saved)
    }

    @Test
    fun `declining the file reports Declined`() = runTest(scheduler) {
        val backend = backend()
        val viewModel = ReceiveViewModel(backend)
        backend.demo.peerSendsSampleFile()
        advanceUntilIdle()

        viewModel.decline()
        advanceUntilIdle()

        assertTrue("actual: ${viewModel.state.value}", viewModel.state.value is ReceiveUiState.Declined)
    }
}
