package com.example.bookswap.ui.exchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.bookswap.data.model.MeetingRules
import java.util.Date

/**
 * Cancellation dialog (Epic 10). One dialog with two modes, chosen by the 2-hour rule:
 *
 *  - **Standard** (more than 2h out): unrestricted, reason optional.
 *  - **Emergency** (inside 2h, or already past): reason is mandatory and the copy says so.
 *
 * `now` is captured when the dialog opens; the authoritative re-check happens in
 * [ExchangeDetailViewModel.cancel] at the moment of the tap, so a dialog left open across
 * the boundary can't sneak a reasonless cancel through.
 */
@Composable
fun CancelMeetingDialog(
    meetingTime: Date?,
    onDismiss: () -> Unit,
    onConfirm: (reason: String) -> Unit,
) {
    val now = remember { Date() }
    val isEmergency = remember { MeetingRules.requiresEmergencyReason(meetingTime, now) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isEmergency) R.string.cancel_emergency_title else R.string.cancel_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val countdown = meetingTime?.let { timeUntilLabel(it, now) }
                Text(
                    when {
                        isEmergency && countdown != null ->
                            stringResource(R.string.cancel_emergency_body, countdown)
                        isEmergency -> stringResource(R.string.cancel_emergency_body_passed)
                        else -> stringResource(R.string.cancel_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.cancel_books_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = {
                        Text(
                            stringResource(
                                if (isEmergency) R.string.cancel_reason_required
                                else R.string.cancel_reason_optional,
                            ),
                        )
                    },
                    isError = isEmergency && reason.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim()) },
                enabled = !isEmergency || reason.isNotBlank(),
            ) {
                Text(
                    stringResource(
                        if (isEmergency) R.string.cancel_emergency_confirm
                        else R.string.cancel_confirm,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_keep)) }
        },
    )
}
