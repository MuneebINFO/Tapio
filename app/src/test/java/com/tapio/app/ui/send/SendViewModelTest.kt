package com.tapio.app.ui.send

import com.tapio.app.data.FakeTransferBackend
import com.tapio.app.ui.model.SendUiState
import com.tapio.core.common.SharedContent
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

@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelTest {

    private val scheduler = TestCoroutineScheduler()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val file = SharedContent.File("test://holiday.jpg", "holiday.jpg", "image/jpeg", PAYLOAD.size.toLong())

    private fun backend() = FakeTransferBackend(
        fileSource = InMemoryFileSource(mapOf(file.uri to PAYLOAD)),
        transferConfig = TransferConfig(
            dispatcher = Dispatchers.Unconfined,
            progressIntervalMs = 0L,
            clock = { 0L },
        ),
    )

    @Test
    fun `picking a file moves to ReadyToTap`() = runTest(scheduler) {
        val viewModel = SendViewModel(backend())

        viewModel.onFilePicked(file)

        val state = viewModel.state.value
        assertTrue(state is SendUiState.ReadyToTap)
        assertEquals(file, (state as SendUiState.ReadyToTap).file)
    }

    @Test
    fun `a simulated peer pickup drives the transfer to Sent`() = runTest(scheduler) {
        val backend = backend()
        val viewModel = SendViewModel(backend)

        viewModel.onFilePicked(file)
        backend.demo.peerPicksUpFile()
        advanceUntilIdle()

        assertTrue("actual: ${viewModel.state.value}", viewModel.state.value is SendUiState.Sent)
    }

    @Test
    fun `reset returns to the picker`() = runTest(scheduler) {
        val viewModel = SendViewModel(backend())
        viewModel.onFilePicked(file)

        viewModel.reset()

        assertEquals(SendUiState.PickingFile, viewModel.state.value)
    }

    private companion object {
        val PAYLOAD = ByteArray(64 * 1024) { it.toByte() }
    }
}
