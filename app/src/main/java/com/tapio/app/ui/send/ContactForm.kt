package com.tapio.app.ui.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tapio.app.R
import com.tapio.app.ui.theme.TapioTheme

/**
 * Name + number + optional company, with a shortcut to fill in this phone's own
 * number when it is available.
 */
@Composable
fun ContactForm(
    ownNumber: String?,
    onShare: (name: String, number: String, organization: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var organization by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.contact_form_title),
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.contact_form_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text(stringResource(R.string.contact_form_number)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = organization,
            onValueChange = { organization = it },
            label = { Text(stringResource(R.string.contact_form_org)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (ownNumber != null) {
            TextButton(onClick = { number = ownNumber }) {
                Text(stringResource(R.string.contact_form_use_mine))
            }
        }

        Button(
            onClick = { onShare(name, number, organization.ifBlank { null }) },
            enabled = name.isNotBlank() && number.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(R.string.contact_form_share), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview
@Composable
private fun ContactFormPreview() {
    TapioTheme {
        ContactForm(ownNumber = "+33 6 00 00 00 00", onShare = { _, _, _ -> })
    }
}
