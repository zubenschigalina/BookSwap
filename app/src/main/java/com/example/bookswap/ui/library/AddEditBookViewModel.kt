package com.example.bookswap.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.model.Book
import com.example.bookswap.data.remote.lookupIsbn as fetchIsbn
import com.example.bookswap.data.repository.AuthRepository
import com.example.bookswap.data.repository.BookRepository
import com.example.bookswap.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditBookUiState(
    val existing: Book? = null,
    val isLoadingBook: Boolean = false,
    val isLookingUp: Boolean = false,
    val isSaving: Boolean = false,
    /** Title/author fetched from an ISBN lookup, consumed by the form once. */
    val lookup: Pair<String, String>? = null,
    val message: String? = null,
    val error: String? = null,
)

private const val MAX_IMAGES = 5

/**
 * Backs the add/edit book form (Epic 3): ISBN auto-fill, image constraints (1–5),
 * and create/update via [BookRepository]. `ownerCity` is denormalized from the
 * user's profile so discovery can filter by city.
 */
class AddEditBookViewModel(
    private val bookRepository: BookRepository = BookRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditBookUiState())
    val uiState: StateFlow<AddEditBookUiState> = _uiState.asStateFlow()

    /** Loads an existing book for editing; call with null for the add flow. */
    fun load(bookId: String?) {
        if (bookId == null || _uiState.value.existing?.id == bookId) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBook = true) }
            val book = bookRepository.getBook(bookId)
            _uiState.update { it.copy(existing = book, isLoadingBook = false) }
        }
    }

    fun lookupIsbn(isbn: String) {
        if (isbn.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLookingUp = true, error = null, message = null) }
            val result = fetchIsbn(isbn)
            _uiState.update {
                if (result == null) {
                    it.copy(isLookingUp = false, message = "No match found — enter details manually.")
                } else {
                    it.copy(isLookingUp = false, lookup = result.title to result.author)
                }
            }
        }
    }

    fun consumeLookup() = _uiState.update { it.copy(lookup = null) }
    fun consumeMessages() = _uiState.update { it.copy(message = null, error = null) }

    /**
     * Validates and saves. Requires: title, author, category, language, at least one
     * image (kept or new), the condition-accurate confirmation, and no more than 5 images.
     */
    fun save(
        title: String,
        author: String,
        isbn: String,
        category: String,
        language: String,
        description: String,
        condition: String,
        keptImageUrls: List<String>,
        newImageUris: List<Uri>,
        conditionConfirmed: Boolean,
        onSaved: () -> Unit,
    ) {
        val firebaseUser = authRepository.currentUser ?: return
        val totalImages = keptImageUrls.size + newImageUris.size

        val error = when {
            title.isBlank() || author.isBlank() -> "Title and author are required."
            category.isBlank() || language.isBlank() -> "Pick a category and language."
            totalImages < 1 -> "Add at least one photo."
            totalImages > MAX_IMAGES -> "A book can have at most $MAX_IMAGES photos."
            !conditionConfirmed -> "Please confirm the condition is accurate."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val existing = _uiState.value.existing
                val ownerCity = existing?.ownerCity?.ifBlank { null }
                    ?: userRepository.getUser(firebaseUser.uid)?.city.orEmpty()

                val book = (existing ?: Book()).copy(
                    ownerId = firebaseUser.uid,
                    ownerCity = ownerCity,
                    title = title.trim(),
                    author = author.trim(),
                    isbn = isbn.trim(),
                    category = category,
                    language = language,
                    description = description.trim(),
                    condition = condition,
                )
                if (existing == null) {
                    bookRepository.addBook(book, newImageUris)
                } else {
                    bookRepository.updateBook(book, keptImageUrls, newImageUris)
                }
                _uiState.update { it.copy(isSaving = false) }
                onSaved()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.localizedMessage) }
            }
        }
    }
}
