package com.tapio.app.ui.receive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import com.tapio.app.ui.components.PersonAvatar
import com.tapio.app.ui.theme.TapioTheme
import com.tapio.core.common.SharedContent

/**
 * The "Save this contact?" modal, shown the instant a contact card arrives and
 * verifies. Accepting opens the system "add contact" screen pre-filled with the
 * name the sender chose.
 */
@Composable
fun SaveContactDialog(
    card: SharedContent.ContactCard,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                stringResource(R.string.save_contact_title),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonAvatar(modifier = Modifier.size(96.dp))
                Text(card.displayName, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    text = listOfNotNull(card.phoneNumber, card.organization).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.save_contact_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.save_contact_decline)) }
        },
    )
}

@Preview
@Composable
private fun SaveContactDialogPreview() {
    TapioTheme {
        SaveContactDialog(
            card = SharedContent.ContactCard("Jean Dupont", "+33 6 12 34 56 78", "Café des Amis"),
            onAccept = {},
            onDecline = {},
        )
    }
}
