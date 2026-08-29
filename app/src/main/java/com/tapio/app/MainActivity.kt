package com.tapio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.core.nfc.android.AndroidNfcAvailabilityChecker
import com.tapio.core.nfc.domain.NfcAvailability

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TapioTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                    val context = LocalContext.current
                    val availability = remember {
                        AndroidNfcAvailabilityChecker(context).current()
                    }
                    HomeScreen(
                        availability = availability,
                        modifier = Modifier.padding(insets),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(availability: NfcAvailability, modifier: Modifier = Modifier) {
    val statusRes = when (availability) {
        NfcAvailability.Ready -> R.string.nfc_ready
        NfcAvailability.Disabled -> R.string.nfc_disabled
        NfcAvailability.Unsupported -> R.string.nfc_unsupported
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(statusRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    TapioTheme(dynamicColor = false) {
        HomeScreen(availability = NfcAvailability.Ready)
    }
}
