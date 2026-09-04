package com.tapio.app

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tapio.app.data.ActiveTransfer
import com.tapio.app.data.ContactSaver
import com.tapio.app.data.HandshakeTokenCodecBridge
import com.tapio.app.data.tapioHandshakeToken
import com.tapio.app.ui.prompt.PromptUiState
import com.tapio.app.ui.prompt.TransferPrompt
import com.tapio.app.ui.prompt.TransferPromptViewModel
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.core.nfc.android.HceTokenAdvertiser
import com.tapio.core.nfc.android.TapioTagReader
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.SessionToken
import kotlinx.coroutines.delay
import java.util.UUID

private const val TAG = "TapioReceive"
private const val AUTO_CLOSE_MS = 2_200L
private const val WAITING_TIMEOUT_MS = 20_000L

/**
 * The popup a tap opens. Themed transparent and launched over whatever the user was
 * doing, so Tapio never takes over the screen — it just asks, transfers, and closes.
 *
 * Reached two ways: the NFC intent filters in the manifest (Tapio closed), or
 * [MainActivity]'s reader mode handing over a token it already read.
 *
 * When the launching tap was too brief to carry a token, the popup does **not**
 * close: it shows "touch again" and runs its own reader mode, so the retry is caught
 * in-process — no second cold start, which is what made the first tap unreliable.
 */
class TransferPromptActivity : ComponentActivity() {

    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    /** Set while the popup still needs a token; cleared once a transfer is running. */
    private var awaitToken: ((SessionToken) -> Unit)? = null
    private var resumed = false
    private var claimed: UUID? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val token = resolveToken(intent)
        if (token == null && !openWithoutToken()) return
        if (token != null && !claim(token)) {
            finish()
            return
        }

        val backend = (application as TapioApplication).transferBackend
        setContent {
            TapioTheme {
                val viewModel: TransferPromptViewModel = viewModel(
                    factory = TransferPromptViewModel.factory(backend, token),
                )
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state) { onStateChanged(state, viewModel::onTokenRead) }

                TransferPrompt(
                    state = state,
                    onAccept = viewModel::accept,
                    onRefuse = viewModel::refuse,
                    onSave = viewModel::saveFile,
                    onDiscard = viewModel::discardArrival,
                    onAddContact = {
                        (state as? PromptUiState.ContactArrived)?.let {
                            ContactSaver.launch(this@TransferPromptActivity, it.contact.card)
                            viewModel.onContactHandedOff()
                        }
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    /**
     * A tap reached us with no token. Returns false (and closes) when we are the sender:
     * the tap is the other phone reading *our* tag, and opening the receive popup here
     * would switch us into reader mode and kill the emulation it is mid-read of.
     */
    private fun openWithoutToken(): Boolean {
        if (HceTokenAdvertiser.isAdvertising) {
            Log.i(TAG, "ignoring tap — this device is the sender")
            finish()
            return false
        }
        Log.w(TAG, "prompt opened without a token — waiting for another tap")
        return true
    }

    private suspend fun onStateChanged(state: PromptUiState, onToken: (SessionToken) -> Unit) {
        when (state) {
            PromptUiState.WaitingForTap -> {
                setWaiting(onToken)
                delay(WAITING_TIMEOUT_MS)
                Log.w(TAG, "no retry tap — closing")
                finish()
            }

            is PromptUiState.Done -> {
                delay(AUTO_CLOSE_MS)
                finish()
            }

            // Reader mode is only for the retry tap; the transfer itself must not hold
            // the NFC controller.
            else -> setWaiting(null)
        }
    }

    /** A fresh NFC dispatch while the popup is already up (`singleTop`). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveToken(intent)?.let { deliver(it) }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        applyReaderMode()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        runCatching { nfcAdapter?.disableReaderMode(this) }
    }

    private fun setWaiting(accept: ((SessionToken) -> Unit)?) {
        awaitToken = accept
        applyReaderMode()
    }

    private fun applyReaderMode() {
        val adapter = nfcAdapter ?: return
        if (!resumed || awaitToken == null) {
            runCatching { adapter.disableReaderMode(this) }
            return
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        val ok = runCatching {
            adapter.enableReaderMode(this, { tag ->
                when (val outcome = TapioTagReader.read(tag)) {
                    is HandshakeOutcome.Success -> runOnUiThread { deliver(outcome.token) }
                    is HandshakeOutcome.Failure -> Log.w(TAG, "retry read failed: ${outcome.error.message}")
                }
            }, flags, null)
        }.isSuccess
        Log.i(TAG, "prompt reader mode ON (ok=$ok)")
    }

    private fun deliver(token: SessionToken) {
        awaitToken?.let { accept ->
            if (!claim(token)) return
            setWaiting(null)
            accept(token)
        }
    }

    private fun claim(token: SessionToken): Boolean =
        ActiveTransfer.claim(token.sessionId).also { if (it) claimed = token.sessionId }

    override fun onDestroy() {
        super.onDestroy()
        claimed?.let(ActiveTransfer::release)
    }

    private fun resolveToken(source: Intent?): SessionToken? {
        source ?: return null
        HandshakeTokenCodecBridge.decode(source.getStringExtra(EXTRA_TOKEN))?.let { return it }
        return runCatching { source.tapioHandshakeToken() }.getOrNull()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    companion object {
        private const val EXTRA_TOKEN = "com.tapio.app.extra.PROMPT_TOKEN"

        /** Opens the popup for a token already read in-app (reader mode). */
        fun open(context: Context, token: SessionToken) {
            val intent = Intent(context, TransferPromptActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TOKEN, HandshakeTokenCodecBridge.encode(token))
            context.startActivity(intent)
        }
    }
}
