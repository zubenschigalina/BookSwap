package com.example.bookswap.ui.exchange

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bookswap.R
import java.util.Calendar
import java.util.Date

/**
 * Date + time + public location picker (Epic 8). Used both for the proposer's initial
 * schedule and for either party's reschedule request — [title] and [confirmLabel] say
 * which.
 *
 * Location is free text pre-filled from the user's saved preferred meeting point; Epic 8
 * restricts meetings to public places, which the hint states but cannot enforce without a
 * places API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleMeetingDialog(
    title: String,
    confirmLabel: String,
    initialDateTime: Date?,
    initialLocation: String,
    onDismiss: () -> Unit,
    onConfirm: (Date, String) -> Unit,
) {
    val startCalendar = remember {
        Calendar.getInstance().apply {
            time = initialDateTime ?: Date()
            if (initialDateTime == null) add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = startCalendar.timeInMillis,
        // Epic 8 is about arranging a future hand-off, so past dates are not selectable.
        selectableDates = remember {
            object : androidx.compose.material3.SelectableDates {
                private val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayStart
            }
        },
    )
    val timePickerState = rememberTimePickerState(
        initialHour = startCalendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = startCalendar.get(Calendar.MINUTE),
    )

    var location by remember { mutableStateOf(initialLocation) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val selectedDateTime = combineDateAndTime(
        dateMillis = datePickerState.selectedDateMillis ?: startCalendar.timeInMillis,
        hour = timePickerState.hour,
        minute = timePickerState.minute,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    selectedDateTime.formatMeetingDateTime(),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.meeting_pick_date)) }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.meeting_pick_time)) }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.meeting_location)) },
                    supportingText = { Text(stringResource(R.string.meeting_location_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedDateTime, location.trim()) },
                enabled = location.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.city_dialog_cancel)) }
        },
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.meeting_done))
                }
            },
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.meeting_pick_time)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.meeting_done))
                }
            },
        )
    }
}
