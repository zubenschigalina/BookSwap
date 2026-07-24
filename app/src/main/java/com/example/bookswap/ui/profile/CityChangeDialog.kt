package com.example.bookswap.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bookswap.R

/**
 * Prompts for a new city and a mandatory reason (Epic 2, Scenario 2). The reason is
 * persisted to city_change_history for moderation/fraud audit.
 */
@Composable
fun CityChangeDialog(
    currentCity: String,
    onConfirm: (newCity: String, reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newCity by remember { mutableStateOf(currentCity) }
    var reason by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.city_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newCity,
                    onValueChange = { newCity = it },
                    label = { Text(stringResource(R.string.city_dialog_new_city)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.city_dialog_reason)) },
                    placeholder = { Text(stringResource(R.string.city_dialog_reason_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                if (showError) {
                    Text(
                        stringResource(R.string.city_dialog_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newCity.isBlank() || reason.isBlank() || newCity.trim() == currentCity) {
                    showError = true
                } else {
                    onConfirm(newCity, reason)
                }
            }) {
                Text(stringResource(R.string.city_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.city_dialog_cancel))
            }
        },
    )
}
