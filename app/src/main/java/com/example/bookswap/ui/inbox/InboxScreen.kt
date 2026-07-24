package com.example.bookswap.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.example.bookswap.data.model.InterestAlert
import com.example.bookswap.data.model.Proposal
import com.example.bookswap.data.model.ProposalStatus

/**
 * Inbox (Epics 6 & 7): a "Proposals" queue with sorting + accept/reject, and an
 * "Interest" list of who favorited the owner's books (each links to that user's library).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onViewUser: (userId: String, name: String) -> Unit,
    viewModel: InboxViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.inbox_title)) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                InboxTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = uiState.tab == tab,
                        onClick = { viewModel.setTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index, InboxTab.entries.size),
                    ) {
                        Text(
                            stringResource(
                                if (tab == InboxTab.PROPOSALS) R.string.inbox_proposals else R.string.inbox_interest,
                            ),
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                uiState.tab == InboxTab.PROPOSALS -> ProposalsTab(
                    proposals = uiState.proposals,
                    sort = uiState.sort,
                    onSortChange = viewModel::setSort,
                    onAccept = viewModel::accept,
                    onReject = viewModel::reject,
                )

                else -> InterestTab(
                    alerts = uiState.alerts,
                    onView = { alert ->
                        viewModel.markAlertRead(alert.id)
                        onViewUser(alert.actorId, alert.actorName)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProposalsTab(
    proposals: List<Proposal>,
    sort: ProposalSort,
    onSortChange: (ProposalSort) -> Unit,
    onAccept: (Proposal) -> Unit,
    onReject: (String) -> Unit,
) {
    if (proposals.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.inbox_no_proposals), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        SortDropdown(sort = sort, onSortChange = onSortChange)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(proposals, key = { it.id }) { proposal ->
                ProposalCard(proposal = proposal, onAccept = { onAccept(proposal) }, onReject = { onReject(proposal.id) })
            }
        }
    }
}

@Composable
private fun SortDropdown(sort: ProposalSort, onSortChange: (ProposalSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.inbox_sort_by, sort.label))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProposalSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onSortChange(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ProposalCard(proposal: Proposal, onAccept: () -> Unit, onReject: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.inbox_wants, proposal.proposerName, proposal.requestedBookTitle),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.inbox_offers),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            proposal.offeredBookTitles.forEach { title ->
                Text("• $title", style = MaterialTheme.typography.bodyMedium)
            }

            if (proposal.status == ProposalStatus.PENDING.name) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.inbox_reject))
                    }
                    Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.inbox_accept))
                    }
                }
            } else {
                StatusPill(status = proposal.status)
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) {
        ProposalStatus.ACCEPTED.name -> MaterialTheme.colorScheme.primary
        ProposalStatus.REJECTED.name -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(ProposalStatus.labelOf(status), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun InterestTab(alerts: List<InterestAlert>, onView: (InterestAlert) -> Unit) {
    if (alerts.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.inbox_no_interest), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(alerts, key = { it.id }) { alert ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.inbox_interested, alert.actorName, alert.bookTitle),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (alert.read) null else androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onView(alert) }) {
                        Text(stringResource(R.string.inbox_view_library))
                    }
                }
            }
        }
    }
}
