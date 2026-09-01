package com.tapio.app

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tapio.app.data.IncomingTransferNotifier
import com.tapio.app.data.TransferBackend
import com.tapio.app.data.tapioHandshakeToken
import com.tapio.app.ui.home.HomeScreen
import com.tapio.app.ui.intro.IntroScreen
import com.tapio.app.ui.receive.ReceiveScreen
import com.tapio.app.ui.send.SendScreen
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.core.nfc.domain.SessionToken

class MainActivity : ComponentActivity() {

    private val incomingToken = mutableStateOf<SessionToken?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        incomingToken.value = readHandshake(intent)
        val backend = (application as TapioApplication).transferBackend

        setContent {
            TapioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Launched by a tap? Skip the intro and go straight to the accept prompt.
                    var showIntro by rememberSaveable { mutableStateOf(incomingToken.value == null) }

                    TapioApp(
                        backend = backend,
                        incomingToken = incomingToken,
                        onIncomingHandled = { incomingToken.value = null },
                    )

                    AnimatedVisibility(visible = showIntro, exit = fadeOut(tween(200))) {
                        IntroScreen(onFinished = { showIntro = false })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readHandshake(intent)?.let { incomingToken.value = it }
    }

    private fun readHandshake(source: Intent?): SessionToken? {
        source ?: return null
        source.tapioHandshakeToken()?.let { return it }
        if (source.action == IncomingTransferNotifier.ACTION_INCOMING_TRANSFER) {
            return IncomingTransferNotifier.decodeToken(
                source.getStringExtra(IncomingTransferNotifier.EXTRA_HANDSHAKE_TOKEN),
            )
        }
        return null
    }
}

private enum class Screen { HOME, SEND, RECEIVE }

@Composable
private fun TapioApp(
    backend: TransferBackend,
    incomingToken: State<SessionToken?>,
    onIncomingHandled: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val token by incomingToken

    LaunchedEffect(token) {
        if (token != null) screen = Screen.RECEIVE
    }

    BackHandler(enabled = screen != Screen.HOME) {
        if (screen == Screen.RECEIVE) onIncomingHandled()
        screen = Screen.HOME
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            onSend = { screen = Screen.SEND },
            onReceive = { screen = Screen.RECEIVE },
            modifier = Modifier.fillMaxSize(),
        )

        Screen.SEND -> SendScreen(
            backend = backend,
            onBack = { screen = Screen.HOME },
            modifier = Modifier.fillMaxSize(),
        )

        Screen.RECEIVE -> ReceiveScreen(
            backend = backend,
            onBack = {
                onIncomingHandled()
                screen = Screen.HOME
            },
            modifier = Modifier.fillMaxSize(),
            incomingToken = token,
        )
    }
}
