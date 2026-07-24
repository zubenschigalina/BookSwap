package com.example.bookswap.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.model.Book
import com.example.bookswap.data.repository.AuthRepository
import com.example.bookswap.data.repository.BookRepository
import com.example.bookswap.data.repository.FavoriteRepository
import com.example.bookswap.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiscoveryMode { SWIPE, GRID }

/**
 * Discovery filters (Epic 5). A null category/language/condition means "any"; author
 * and keyword are free-text substring matches. Distance radius is deferred (decision
 * #6: same-city MVP, no geolocation yet).
 */
data class DiscoveryFilter(
    val category: String? = null,
    val language: String? = null,
    val condition: String? = null,
    val author: String = "",
    val keyword: String = "",
) {
    val isActive: Boolean
        get() = category != null || language != null || condition != null ||
            author.isNotBlank() || keyword.isNotBlank()

    fun matches(book: Book): Boolean {
        if (category != null && book.category != category) return false
        if (language != null && book.language != language) return false
        if (condition != null && book.condition != condition) return false
        if (author.isNotBlank() && !book.author.contains(author, ignoreCase = true)) return false
        if (keyword.isNotBlank()) {
            val hay = "${book.title} ${book.author} ${book.description}"
            if (!hay.contains(keyword, ignoreCase = true)) return false
        }
        return true
    }
}

data class DiscoveryUiState(
    val isLoading: Boolean = true,
    val mode: DiscoveryMode = DiscoveryMode.SWIPE,
    val city: String = "",
    val books: List<Book> = emptyList(),
    /** Books swiped this session — removed from both the swipe stack and the grid. */
    val swipedIds: Set<String> = emptySet(),
    val filter: DiscoveryFilter = DiscoveryFilter(),
    val showFilterSheet: Boolean = false,
    /** True while at least one swipe can be undone this session. */
    val canUndo: Boolean = false,
    val selected: Book? = null,
    val error: String? = null,
) {
    /** Cross-view source of truth: unswiped books that pass the active filter. */
    val remaining: List<Book>
        get() = books.filter { it.id !in swipedIds && filter.matches(it) }
}

/**
 * Backs both discovery views (Epic 4) and their filtering/undo (Epic 5). A single
 * instance is shared between the swipe stack and the grid, so a like/reject in one view
 * immediately updates the other via [DiscoveryUiState.swipedIds].
 *
 * A swipe-right / Like records a Favorite and alerts the owner (Epic 6, decision #1).
 * Undo reverses the most recent swipe (and its favorite, if any).
 */
class DiscoveryViewModel(
    private val bookRepository: BookRepository = BookRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val favoriteRepository: FavoriteRepository = FavoriteRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private var currentUserName: String = ""

    /** Undo history for this session; most recent last. */
    private data class SwipeAction(val book: Book, val liked: Boolean)
    private val history = ArrayDeque<SwipeAction>()

    init { refresh() }

    fun refresh() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val user = userRepository.getUser(uid)
                currentUserName = user?.firstName.orEmpty()
                val city = user?.city.orEmpty()
                val books = if (city.isBlank()) emptyList() else bookRepository.discover(city, uid)
                history.clear()
                _uiState.update {
                    it.copy(
                        isLoading = false, city = city, books = books,
                        swipedIds = emptySet(), canUndo = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }

    fun swipe(book: Book, liked: Boolean) {
        history.addLast(SwipeAction(book, liked))
        _uiState.update {
            it.copy(swipedIds = it.swipedIds + book.id, selected = null, canUndo = true)
        }
        if (liked) {
            val uid = authRepository.currentUser?.uid ?: return
            viewModelScope.launch {
                runCatching { favoriteRepository.addFavorite(book, uid, currentUserName) }
            }
        }
    }

    /** Undo the single most recent swipe (Epic 5), reversing its favorite if it was a Like. */
    fun undo() {
        val last = history.removeLastOrNull() ?: return
        _uiState.update {
            it.copy(swipedIds = it.swipedIds - last.book.id, canUndo = history.isNotEmpty())
        }
        if (last.liked) {
            val uid = authRepository.currentUser?.uid ?: return
            viewModelScope.launch {
                runCatching { favoriteRepository.removeFavorite(uid, last.book.id) }
            }
        }
    }

    /** Reset Discovery (Epic 5): bring back every book swiped this session. */
    fun resetDiscovery() {
        history.clear()
        _uiState.update { it.copy(swipedIds = emptySet(), canUndo = false) }
    }

    fun setMode(mode: DiscoveryMode) = _uiState.update { it.copy(mode = mode) }

    fun openFilters() = _uiState.update { it.copy(showFilterSheet = true) }
    fun dismissFilters() = _uiState.update { it.copy(showFilterSheet = false) }
    fun applyFilter(filter: DiscoveryFilter) =
        _uiState.update { it.copy(filter = filter, showFilterSheet = false) }
    fun clearFilter() = _uiState.update { it.copy(filter = DiscoveryFilter()) }

    fun select(book: Book) = _uiState.update { it.copy(selected = book) }
    fun clearSelection() = _uiState.update { it.copy(selected = null) }
}
