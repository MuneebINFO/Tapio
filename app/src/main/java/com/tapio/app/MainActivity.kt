package com.tapio.app

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapio.app.data.TransferBackend
import com.tapio.app.ui.components.PermissionGate
import com.tapio.app.ui.home.HomeScreen
import com.tapio.app.ui.intro.IntroScreen
import com.tapio.app.ui.send.SendScreen
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.core.nfc.android.TapioTagReader
import com.tapio.core.nfc.domain.HandshakeOutcome
import com.tapio.core.nfc.domain.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "TapioReceive"

/**
 * The visible app: home + the send flow. Receiving never happens here — a tap opens
 * [TransferPromptActivity] as a popup instead.
 *
 * While this activity is foreground and *not* sending, it runs NFC reader mode so a
 * tap is caught instantly; it then opens the same popup with the token it read.
 */
class MainActivity : ComponentActivity() {

    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var readerSuppressed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val backend = (application as TapioApplication).transferBackend

        setContent {
            TapioTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showIntro by rememberSaveable { mutableStateOf(true) }

                    // Demo "incoming tap" from the home screen (fake backend only).
                    val demoToken by remember(backend) {
                        backend.demo?.incomingToken ?: MutableStateFlow<SessionToken?>(null)
                    }.collectAsStateWithLifecycle()
                    LaunchedEffect(demoToken) {
                        demoToken?.let {
                            backend.demo?.clearIncoming()
                            TransferPromptActivity.open(this@MainActivity, it)
                        }
                    }

                    TapioApp(backend, onSending = ::setReaderSuppressed)

                    AnimatedVisibility(visible = showIntro, exit = fadeOut(tween(200))) {
                        IntroScreen(onFinished = { showIntro = false })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyReaderMode()
    }

    override fun onPause() {
        super.onPause()
        runCatching { nfcAdapter?.disableReaderMode(this) }
        runCatching { nfcAdapter?.disableForegroundDispatch(this) }
    }

    /** Tags captured while sending land back here and are deliberately dropped. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "tag ignored — we are the sender")
    }

    private fun selfDispatchIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_MUTABLE,
    )

    private fun setReaderSuppressed(suppressed: Boolean) {
        readerSuppressed = suppressed
        applyReaderMode()
    }

    private fun applyReaderMode() {
        val adapter = nfcAdapter ?: run {
            Log.i(TAG, "no NFC adapter")
            return
        }
        if (readerSuppressed) {
            runCatching { adapter.disableReaderMode(this) }
            // While sending we emulate a tag, but the controller keeps polling too, so we
            // can discover the *peer's* empty tag and let the platform launch the receive
            // popup on ourselves. Foreground dispatch keeps those tags here, where we
            // ignore them, instead of hijacking our own transfer.
            runCatching { adapter.enableForegroundDispatch(this, selfDispatchIntent(), null, null) }
            Log.i(TAG, "reader mode OFF (sending, tags captured)")
            return
        }
        runCatching { adapter.disableForegroundDispatch(this) }
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        val ok = runCatching {
            adapter.enableReaderMode(this, { tag ->
                Log.i(TAG, "reader-mode tap")
                when (val outcome = TapioTagReader.read(tag)) {
                    is HandshakeOutcome.Success -> TransferPromptActivity.open(this, outcome.token)
                    is HandshakeOutcome.Failure -> Log.w(TAG, "read failed: ${outcome.error.message}")
                }
            }, flags, null)
        }.isSuccess
        Log.i(TAG, "reader mode ON (ok=$ok)")
    }
}

private enum class Screen { HOME, SEND }

@Composable
private fun TapioApp(backend: TransferBackend, onSending: (Boolean) -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val sendingCallback by rememberUpdatedState(onSending)

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    DisposableEffect(screen) {
        sendingCallback(screen == Screen.SEND)
        onDispose { }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            onSend = { screen = Screen.SEND },
            demo = backend.demo,
            modifier = Modifier.fillMaxSize(),
        )

        Screen.SEND -> PermissionGate {
            SendScreen(
                backend = backend,
                onBack = { screen = Screen.HOME },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
