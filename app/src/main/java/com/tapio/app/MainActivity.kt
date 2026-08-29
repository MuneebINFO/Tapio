package com.tapio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tapio.app.data.TransferBackend
import com.tapio.app.ui.home.HomeScreen
import com.tapio.app.ui.receive.ReceiveScreen
import com.tapio.app.ui.send.SendScreen
import com.tapio.app.ui.theme.TapioTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val backend = (application as TapioApplication).transferBackend

        setContent {
            TapioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TapioApp(backend)
                }
            }
        }
    }
}

private enum class Screen { HOME, SEND, RECEIVE }

@Composable
private fun TapioApp(backend: TransferBackend) {
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    when (screen) {
        Screen.HOME -> HomeScreen(
            onSend = { screen = Screen.SEND },
            onReceive = { screen = Screen.RECEIVE },
        )

        Screen.SEND -> SendScreen(backend = backend, onBack = { screen = Screen.HOME })
        Screen.RECEIVE -> ReceiveScreen(backend = backend, onBack = { screen = Screen.HOME })
    }
}
