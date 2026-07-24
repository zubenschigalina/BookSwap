package com.example.bookswap.ui.discovery

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bookswap.R
import com.example.bookswap.data.model.Book
import com.example.bookswap.data.model.BookCondition
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Tinder-style card stack. The top card is draggable; dragging past a threshold (or
 * tapping the reject/like buttons) reports a swipe. Cards behind peek through, scaled down.
 */
@Composable
fun SwipeCardStack(
    books: List<Book>,
    onSwiped: (Book, Boolean) -> Unit,
    onCardTap: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (books.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.discovery_empty), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    Column(modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Draw up to 3 cards back-to-front; the last one drawn is the interactive top card.
            val visible = books.take(3).reversed()
            visible.forEachIndexed { index, book ->
                val depth = visible.lastIndex - index // 0 = top card
                if (depth == 0) {
                    TopSwipeCard(book = book, onSwiped = onSwiped, onTap = { onCardTap(book) })
                } else {
                    val scale = 1f - depth * 0.04f
                    Card(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                    ) {}
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
        ) {
            val top = books.first()
            FloatingActionButton(onClick = { onSwiped(top, false) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.discovery_reject))
            }
            FloatingActionButton(onClick = { onSwiped(top, true) }) {
                Icon(Icons.Default.Favorite, contentDescription = stringResource(R.string.discovery_like))
            }
        }
    }
}

@Composable
private fun TopSwipeCard(book: Book, onSwiped: (Book, Boolean) -> Unit, onTap: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(book.id) { Animatable(0f) }
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val threshold = screenWidthPx * 0.30f

    Card(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / screenWidthPx) * 12f
            }
            .pointerInput(book.id) {
                detectDragGestures(
                    onDragEnd = {
                        val x = offsetX.value
                        if (abs(x) > threshold) {
                            val liked = x > 0
                            val target = if (liked) screenWidthPx * 1.5f else -screenWidthPx * 1.5f
                            scope.launch {
                                offsetX.animateTo(target, tween(250))
                                onSwiped(book, liked)
                            }
                        } else {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                    },
                )
            }
            .pointerInput(book.id) {
                detectTapGestures(onTap = { onTap() })
            },
    ) {
        BookCardContent(book = book)
    }
}

@Composable
private fun BookCardContent(book: Book) {
    Box(modifier = Modifier.fillMaxSize()) {
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .padding(16.dp),
        ) {
            Text(book.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.inverseOnSurface)
            Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.inverseOnSurface)
            Text(
                "${BookCondition.labelOf(book.condition)} · ${book.category} · ${book.language}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}
