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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bookswap.R
import com.example.bookswap.data.model.Book
import com.example.bookswap.data.model.BookCondition
import com.example.bookswap.data.model.User
import com.example.bookswap.data.repository.BookRepository
import com.example.bookswap.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InterestedUserUiState(
    val user: User? = null,
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
)

/** Read-only view of an interested user's profile + catalog (Epic 6). */
class InterestedUserViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val bookRepository: BookRepository = BookRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(InterestedUserUiState())
    val uiState: StateFlow<InterestedUserUiState> = _uiState.asStateFlow()
    private var loaded = false

    fun load(userId: String) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val user = runCatching { userRepository.getUser(userId) }.getOrNull()
            val books = runCatching { bookRepository.observeMyBooks(userId).first() }.getOrDefault(emptyList())
            _uiState.update { it.copy(user = user, books = books, isLoading = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterestedUserScreen(
    userId: String,
    fallbackName: String,
    onBack: () -> Unit,
    viewModel: InterestedUserViewModel = viewModel(),
) {
    LaunchedEffect(userId) { viewModel.load(userId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val name = uiState.user?.firstName?.ifBlank { fallbackName } ?: fallbackName

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ProfileHeader(user = uiState.user, name = name) }
            item {
                Text(
                    stringResource(R.string.interested_their_books),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (uiState.books.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.interested_no_books),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                items(uiState.books, key = { it.id }) { book -> TheirBookRow(book) }
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: User?, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val photo = user?.profilePhotoUrl
            if (!photo.isNullOrBlank()) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleLarge)
            if (!user?.city.isNullOrBlank()) {
                Text(user!!.city, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TheirBookRow(book: Book) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp, 64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val cover = book.imageUrls.firstOrNull()
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.MenuBook, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${book.author} · ${BookCondition.labelOf(book.condition)}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
