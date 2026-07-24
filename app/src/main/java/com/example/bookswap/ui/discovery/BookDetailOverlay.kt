package com.example.bookswap.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.bookswap.R
import com.example.bookswap.data.model.Book
import com.example.bookswap.data.model.BookCondition

/**
 * Full-screen detail overlay (Epic 4: "Book Detail Overlay Screen"). Like/reject here
 * feed the same shared swipe state, keeping the swipe and grid views in sync.
 */
@Composable
fun BookDetailOverlay(
    book: Book,
    onDismiss: () -> Unit,
    onSwiped: (Book, Boolean) -> Unit,
    onPropose: (Book) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                        val cover = book.imageUrls.firstOrNull()
                        if (cover != null) {
                            AsyncImage(
                                model = cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            androidx.compose.material3.IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(book.title, style = MaterialTheme.typography.headlineSmall)
                        Text(book.author, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${BookCondition.labelOf(book.condition)} · ${book.category} · ${book.language}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (book.ownerCity.isNotBlank()) {
                            Text(book.ownerCity, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (book.description.isNotBlank()) {
                            Text(book.description, style = MaterialTheme.typography.bodyMedium)
                        }

                        // Additional photos beyond the cover.
                        if (book.imageUrls.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(book.imageUrls.drop(1)) { url ->
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                            }
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { onSwiped(book, false) },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = null)
                            Text(stringResource(R.string.discovery_pass), modifier = Modifier.padding(start = 8.dp))
                        }
                        Button(
                            onClick = { onSwiped(book, true) },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null)
                            Text(stringResource(R.string.discovery_like), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    androidx.compose.material3.TextButton(
                        onClick = { onPropose(book) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.propose_swap)) }
                }
            }
        }
    }
}
