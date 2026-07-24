package com.example.bookswap.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.model.Book
import com.example.bookswap.data.repository.AuthRepository
import com.example.bookswap.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
)

/** Backs My Library: the current user's own books, newest first. */
class LibraryViewModel(
    private val bookRepository: BookRepository = BookRepository(),
    authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        val uid = authRepository.currentUser?.uid
        if (uid == null) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                bookRepository.observeMyBooks(uid).collect { books ->
                    _uiState.update { it.copy(books = books, isLoading = false) }
                }
            }
        }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch { bookRepository.deleteBook(id) }
    }
}
