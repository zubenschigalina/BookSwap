package com.example.bookswap.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.model.Favorite
import com.example.bookswap.data.repository.AuthRepository
import com.example.bookswap.data.repository.BookRepository
import com.example.bookswap.data.repository.FavoriteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A favorited book plus its live availability (Epic 6). [available] is resolved against
 * the current book status so the list shows an AVAILABLE / UNAVAILABLE badge even after
 * the owner reserves or removes the book. [ownerId] drives the "Propose swap" target.
 */
data class FavoriteRow(
    val favorite: Favorite,
    val available: Boolean,
    val ownerId: String,
)

data class FavoritesUiState(
    val rows: List<FavoriteRow> = emptyList(),
    val isLoading: Boolean = true,
)

class FavoritesViewModel(
    private val favoriteRepository: FavoriteRepository = FavoriteRepository(),
    private val bookRepository: BookRepository = BookRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        val uid = authRepository.currentUser?.uid
        if (uid != null) viewModelScope.launch {
            favoriteRepository.observeMyFavorites(uid).collect { favorites ->
                // Resolve each favorite's current book to show a live availability badge.
                val rows = favorites.map { fav ->
                    val book = runCatching { bookRepository.getBook(fav.bookId) }.getOrNull()
                    FavoriteRow(
                        favorite = fav,
                        available = book?.status == "AVAILABLE",
                        ownerId = book?.ownerId ?: fav.ownerId,
                    )
                }
                _uiState.update { it.copy(rows = rows, isLoading = false) }
            }
        }
    }

    fun remove(bookId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching { favoriteRepository.removeFavorite(uid, bookId) }
        }
    }
}
