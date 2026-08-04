package com.example.bookswap.ui.exchange

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bookswap.R
import com.example.bookswap.data.model.Meeting
import com.example.bookswap.data.model.MeetingStatus
import com.example.bookswap.data.model.Proposal
import com.example.bookswap.data.model.ProposalStatus

/**
 * The hub for one accepted exchange: what's being swapped, the meeting card
 * (schedule / confirm / reschedule / cancel — Epics 8 & 10) and the way into the
 * now-unlocked chat thread (Epic 9).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeDetailScreen(
    proposalId: String,
    onBack: () -> Unit,
    onOpenChat: (proposalId: String, counterpartName: String) -> Unit,
    viewModel: ExchangeDetailViewModel = viewModel(),
) {
    LaunchedEffect(proposalId) { viewModel.load(proposalId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showScheduleDialog by remember { mutableStateOf(false) }
    var showRescheduleDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exchange_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        val proposal = uiState.proposal
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            proposal == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.exchange_missing)) }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SwapSummaryCard(proposal = proposal, counterpartName = uiState.counterpartName)

                MeetingCard(
                    state = uiState,
                    onSchedule = { showScheduleDialog = true },
                    onReschedule = { showRescheduleDialog = true },
                    onConfirm = viewModel::confirm,
                    onCancel = { showCancelDialog = true },
                )

                if (uiState.isChatUnlocked) {
                    Button(
                        onClick = { onOpenChat(proposalId, uiState.counterpartName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Text(
                            stringResource(R.string.exchange_open_chat),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.exchange_chat_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                uiState.error?.let { message ->
                    Snackbar(
                        action = {
                            OutlinedButton(onClick = viewModel::clearError) {
                                Text(stringResource(R.string.close))
                            }
                        },
                    ) { Text(message) }
                }
            }
        }
    }

    val meeting = uiState.meeting

    if (showScheduleDialog) {
        ScheduleMeetingDialog(
            title = stringResource(R.string.meeting_schedule_title),
            confirmLabel = stringResource(R.string.meeting_send_proposal),
            initialDateTime = meeting?.dateTime,
            initialLocation = meeting?.locationAddress?.takeIf { it.isNotBlank() }
                ?: uiState.defaultMeetingPoint,
            onDismiss = { showScheduleDialog = false },
            onConfirm = { dateTime, location ->
                showScheduleDialog = false
                viewModel.schedule(dateTime, location)
            },
        )
    }

    if (showRescheduleDialog && meeting != null) {
        ScheduleMeetingDialog(
            title = stringResource(R.string.meeting_reschedule_title),
            confirmLabel = stringResource(R.string.meeting_request_change),
            initialDateTime = meeting.proposedDateTime ?: meeting.dateTime,
            initialLocation = meeting.proposedLocationAddress.takeIf { it.isNotBlank() }
                ?: meeting.locationAddress,
            onDismiss = { showRescheduleDialog = false },
            onConfirm = { dateTime, location ->
                showRescheduleDialog = false
                viewModel.requestChange(dateTime, location)
            },
        )
    }

    if (showCancelDialog && meeting != null) {
        CancelMeetingDialog(
            meetingTime = meeting.dateTime,
            onDismiss = { showCancelDialog = false },
            onConfirm = { reason ->
                showCancelDialog = false
                viewModel.cancel(reason) { onBack() }
            },
        )
    }
}

@Composable
private fun SwapSummaryCard(proposal: Proposal, counterpartName: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.exchange_with, counterpartName),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.exchange_requested, proposal.requestedBookTitle),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.exchange_offered),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            proposal.offeredBookTitles.forEach { title ->
                Text("• $title", style = MaterialTheme.typography.bodyMedium)
            }
            if (proposal.status == ProposalStatus.CANCELLED.name) {
                Text(
                    stringResource(R.string.exchange_cancelled_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MeetingCard(
    state: ExchangeDetailUiState,
    onSchedule: () -> Unit,
    onReschedule: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val meeting = state.meeting

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.meeting_heading),
                style = MaterialTheme.typography.titleMedium,
            )

            when {
                // Nothing scheduled yet. Epic 8 puts initiation with the proposer.
                meeting == null -> {
                    if (state.isProposer) {
                        Text(
                            stringResource(R.string.meeting_none_proposer),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = onSchedule,
                            enabled = state.isAccepted && !state.isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.meeting_schedule_action)) }
                    } else {
                        Text(
                            stringResource(
                                R.string.meeting_none_recipient,
                                state.counterpartName,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                meeting.status == MeetingStatus.CANCELLED.name -> CancelledBlock(meeting)

                else -> ScheduledBlock(
                    state = state,
                    meeting = meeting,
                    onReschedule = onReschedule,
                    onConfirm = onConfirm,
                    onCancel = onCancel,
                )
            }
        }
    }
}

@Composable
private fun ScheduledBlock(
    state: ExchangeDetailUiState,
    meeting: Meeting,
    onReschedule: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    StatusPill(
        label = MeetingStatus.labelOf(meeting.status),
        color = if (meeting.status == MeetingStatus.CONFIRMED.name) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiary
        },
    )

    meeting.dateTime?.let { active ->
        Text(active.formatMeetingDateTime(), style = MaterialTheme.typography.titleLarge)
        Text(meeting.locationAddress, style = MaterialTheme.typography.bodyMedium)
    }

    // A reschedule request: the agreed schedule above stays active until this is confirmed.
    if (meeting.hasPendingChange) {
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(
                    if (meeting.proposedById == state.currentUserId) {
                        R.string.meeting_change_yours
                    } else {
                        R.string.meeting_change_theirs
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            meeting.proposedDateTime?.let {
                Text(it.formatMeetingDateTime(), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                meeting.proposedLocationAddress,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (meeting.status != MeetingStatus.CANCELLED.name) {
        if (state.needsMyConfirmation) {
            Button(
                onClick = onConfirm,
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.meeting_confirm)) }
        } else if (!meeting.isFullyConfirmed) {
            Text(
                stringResource(R.string.meeting_awaiting_them, state.counterpartName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onReschedule,
                enabled = !state.isWorking,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.meeting_reschedule)) }
            OutlinedButton(
                onClick = onCancel,
                enabled = !state.isWorking,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.meeting_cancel)) }
        }

        // Epic 10 heads-up so the 2-hour rule isn't a surprise at the moment of cancelling.
        meeting.dateTime?.let { active ->
            timeUntilLabel(active)?.let { countdown ->
                Text(
                    stringResource(R.string.meeting_countdown, countdown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CancelledBlock(meeting: Meeting) {
    StatusPill(
        label = MeetingStatus.labelOf(meeting.status),
        color = MaterialTheme.colorScheme.error,
    )
    if (meeting.cancelReason.isNotBlank()) {
        Text(
            stringResource(R.string.meeting_cancel_reason, meeting.cancelReason),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (meeting.wasEmergencyCancel) {
        Text(
            stringResource(R.string.meeting_was_emergency),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun StatusPill(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
