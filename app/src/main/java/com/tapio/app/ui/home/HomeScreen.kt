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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapio.app.R
import com.tapio.app.ui.brand.TapioMark
import com.tapio.app.ui.components.ActionCard
import com.tapio.app.ui.components.ActionDirection
import com.tapio.app.ui.components.TapioScaffold
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.app.ui.theme.WordmarkStyle

@Composable
fun HomeScreen(
    onSend: () -> Unit,
    onReceive: () -> Unit,
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ActionCard(
                    title = stringResource(R.string.home_send),
                    description = stringResource(R.string.home_send_desc),
                    direction = ActionDirection.SEND,
                    onClick = onSend,
                )
                ActionCard(
                    title = stringResource(R.string.home_receive),
                    description = stringResource(R.string.home_receive_desc),
                    direction = ActionDirection.RECEIVE,
                    onClick = onReceive,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    TapioTheme {
        HomeScreen(onSend = {}, onReceive = {}, modifier = Modifier.fillMaxSize())
    }
}
