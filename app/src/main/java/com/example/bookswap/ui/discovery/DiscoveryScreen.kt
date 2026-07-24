package com.example.bookswap.ui.discovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bookswap.R

/**
 * Discovery hub (Epic 4 + Epic 5). Hosts the swipe/grid toggle, keyword search, the
 * filter sheet, undo, and the shared book detail overlay. Both views read from the same
 * [DiscoveryViewModel], so a like/reject/undo in one is reflected in the other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onPropose: (bookId: String, ownerId: String, title: String) -> Unit,
    viewModel: DiscoveryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.city.isBlank()) {
                            stringResource(R.string.discovery_title)
                        } else {
                            stringResource(R.string.discovery_title_city, uiState.city)
                        },
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = uiState.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.discovery_undo))
                    }
                    IconButton(onClick = viewModel::openFilters) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = stringResource(R.string.filter_title),
                            tint = if (uiState.filter.isActive) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Unspecified,
                        )
                    }
                    // Toggle shows the icon of the *other* view (what tapping switches to).
                    IconButton(onClick = {
                        viewModel.setMode(
                            if (uiState.mode == DiscoveryMode.SWIPE) DiscoveryMode.GRID else DiscoveryMode.SWIPE,
                        )
                    }) {
                        if (uiState.mode == DiscoveryMode.SWIPE) {
                            Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = stringResource(R.string.discovery_grid))
                        } else {
                            Icon(Icons.Default.Style, contentDescription = stringResource(R.string.discovery_swipe))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.filter.keyword,
                onValueChange = { viewModel.applyFilter(uiState.filter.copy(keyword = it)) },
                label = { Text(stringResource(R.string.discovery_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> Center { CircularProgressIndicator() }

                    uiState.city.isBlank() -> Center {
                        Text(
                            stringResource(R.string.discovery_no_city),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp),
                        )
                    }

                    uiState.remaining.isEmpty() -> Center {
                        Text(
                            stringResource(
                                if (uiState.filter.isActive) R.string.discovery_empty_filtered
                                else R.string.discovery_empty,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp),
                        )
                    }

                    uiState.mode == DiscoveryMode.SWIPE -> SwipeCardStack(
                        books = uiState.remaining,
                        onSwiped = viewModel::swipe,
                        onCardTap = viewModel::select,
                    )

                    else -> BookGrid(
                        books = uiState.remaining,
                        onBookTap = viewModel::select,
                    )
                }
            }
        }
    }

    if (uiState.showFilterSheet) {
        DiscoveryFilterSheet(
            filter = uiState.filter,
            onApply = viewModel::applyFilter,
            onReset = { viewModel.resetDiscovery(); viewModel.dismissFilters() },
            onDismiss = viewModel::dismissFilters,
        )
    }

    uiState.selected?.let { book ->
        BookDetailOverlay(
            book = book,
            onDismiss = viewModel::clearSelection,
            onSwiped = viewModel::swipe,
            onPropose = {
                viewModel.clearSelection()
                onPropose(it.id, it.ownerId, it.title)
            },
        )
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
