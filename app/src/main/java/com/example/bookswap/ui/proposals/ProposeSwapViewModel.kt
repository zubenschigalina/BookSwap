package com.example.bookswap.ui.proposals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.model.Book
import com.example.bookswap.data.model.Proposal
import com.example.bookswap.data.repository.AuthRepository
import com.example.bookswap.data.repository.BookRepository
import com.example.bookswap.data.repository.ProposalRepository
import com.example.bookswap.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProposeSwapUiState(
    val requestedTitle: String = "",
    /** The proposer's own AVAILABLE books, the pool to offer from. */
    val myBooks: List<Book> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
)

private const val MAX_OFFERED = 3

/**
 * Proposal composition (Epic 7): pick 1–3 of your own AVAILABLE books to offer for a
 * book you want. Requester-initiated (decision #2) — the proposer offers their books and
 * the book's owner is the recipient.
 */
class ProposeSwapViewModel(
    private val bookRepository: BookRepository = BookRepository(),
    private val proposalRepository: ProposalRepository = ProposalRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProposeSwapUiState())
    val uiState: StateFlow<ProposeSwapUiState> = _uiState.asStateFlow()

    private var requestedBookId: String = ""
    private var recipientId: String = ""
    private var proposerName: String = ""
    private var loaded = false

    fun load(requestedBookId: String, recipientId: String, requestedTitle: String) {
        if (loaded) return
        loaded = true
        this.requestedBookId = requestedBookId
        this.recipientId = recipientId
        val uid = authRepository.currentUser?.uid ?: return
        _uiState.update { it.copy(requestedTitle = requestedTitle) }
        viewModelScope.launch {
            proposerName = userRepository.getUser(uid)?.firstName.orEmpty()
            // A one-shot snapshot of my available books is enough for this form.
            val mine = runCatching {
                bookRepository.observeMyBooks(uid).first()
            }.getOrDefault(emptyList()).filter { it.status == "AVAILABLE" }
            _uiState.update { it.copy(myBooks = mine, isLoading = false) }
        }
    }

    fun toggle(bookId: String) = _uiState.update { state ->
        val selected = state.selectedIds
        when {
            bookId in selected -> state.copy(selectedIds = selected - bookId, error = null)
            selected.size >= MAX_OFFERED -> state.copy(error = "You can offer at most $MAX_OFFERED books.")
            else -> state.copy(selectedIds = selected + bookId, error = null)
        }
    }

    fun send(onSent: () -> Unit) {
        val uid = authRepository.currentUser?.uid ?: return
        val state = _uiState.value
        val chosen = state.myBooks.filter { it.id in state.selectedIds }
        if (chosen.isEmpty()) {
            _uiState.update { it.copy(error = "Pick at least one book to offer.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            try {
                proposalRepository.createProposal(
                    Proposal(
                        proposerId = uid,
                        proposerName = proposerName,
                        recipientId = recipientId,
                        requestedBookId = requestedBookId,
                        requestedBookTitle = state.requestedTitle,
                        offeredBookIds = chosen.map { it.id },
                        offeredBookTitles = chosen.map { it.title },
                    ),
                )
                _uiState.update { it.copy(isSending = false) }
                onSent()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, error = e.localizedMessage) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
