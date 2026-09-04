package com.tapio.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapio.app.R
import com.tapio.app.data.DemoControls
import com.tapio.app.ui.brand.TapioMark
import com.tapio.app.ui.components.ActionCard
import com.tapio.app.ui.components.ActionDirection
import com.tapio.app.ui.components.TapioScaffold
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.app.ui.theme.WordmarkStyle

@Composable
fun HomeScreen(
    onSend: () -> Unit,
    demo: DemoControls?,
    modifier: Modifier = Modifier,
) {
    TapioScaffold(modifier = modifier) { content ->
        Column(
            modifier = content,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            TapioMark(modifier = Modifier.size(76.dp), color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text("Tapio", style = WordmarkStyle, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.weight(1f))

            ActionCard(
                title = stringResource(R.string.home_send),
                description = stringResource(R.string.home_send_desc),
                direction = ActionDirection.SEND,
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
            )

            if (demo != null) {
                Spacer(Modifier.height(4.dp))
                val fileLabel = "▶  " + stringResource(R.string.demo_incoming_file)
                val contactLabel = "▶  " + stringResource(R.string.demo_incoming_contact)
                TextButton(onClick = demo::simulateIncomingFile) {
                    Text(fileLabel, style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = demo::simulateIncomingContact) {
                    Text(contactLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    TapioTheme {
        HomeScreen(onSend = {}, demo = null, modifier = Modifier.fillMaxSize())
    }
}
