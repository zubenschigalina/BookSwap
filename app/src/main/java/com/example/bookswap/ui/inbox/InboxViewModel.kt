package com.example.bookswap.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.model.InterestAlert
import com.example.bookswap.data.model.Proposal
import com.example.bookswap.data.repository.AuthRepository
import com.example.bookswap.data.repository.FavoriteRepository
import com.example.bookswap.data.repository.ProposalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InboxTab { PROPOSALS, INTEREST }

/** Queue sort options (Epic 7). */
enum class ProposalSort(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    REQUESTER("Requester name"),
    BOOK_TITLE("Book title"),
}

data class InboxUiState(
    val tab: InboxTab = InboxTab.PROPOSALS,
    val proposals: List<Proposal> = emptyList(),
    val alerts: List<InterestAlert> = emptyList(),
    val sort: ProposalSort = ProposalSort.NEWEST,
    val isLoading: Boolean = true,
)

/**
 * Proposal inbox + interest alerts (Epics 6 & 7). Incoming proposals are sortable
 * (newest/oldest/requester/title); accepting one runs the state machine in
 * [ProposalRepository.accept] (books lock to RESERVED, competing proposals expire).
 */
class InboxViewModel(
    private val proposalRepository: ProposalRepository = ProposalRepository(),
    private val favoriteRepository: FavoriteRepository = FavoriteRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private var rawProposals: List<Proposal> = emptyList()

    init {
        val uid = authRepository.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                proposalRepository.observeIncoming(uid).collect { list ->
                    rawProposals = list
                    _uiState.update { it.copy(proposals = sorted(list, it.sort), isLoading = false) }
                }
            }
            viewModelScope.launch {
                favoriteRepository.observeInterestAlerts(uid).collect { list ->
                    _uiState.update { it.copy(alerts = list) }
                }
            }
        }
    }

    private fun sorted(list: List<Proposal>, sort: ProposalSort): List<Proposal> = when (sort) {
        ProposalSort.NEWEST -> list.sortedByDescending { it.createdAt }
        ProposalSort.OLDEST -> list.sortedBy { it.createdAt }
        ProposalSort.REQUESTER -> list.sortedBy { it.proposerName.lowercase() }
        ProposalSort.BOOK_TITLE -> list.sortedBy { it.requestedBookTitle.lowercase() }
    }

    fun setTab(tab: InboxTab) = _uiState.update { it.copy(tab = tab) }

    fun setSort(sort: ProposalSort) =
        _uiState.update { it.copy(sort = sort, proposals = sorted(rawProposals, sort)) }

    fun accept(proposal: Proposal) {
        viewModelScope.launch { runCatching { proposalRepository.accept(proposal) } }
    }

    fun reject(proposalId: String) {
        viewModelScope.launch { runCatching { proposalRepository.reject(proposalId) } }
    }

    fun markAlertRead(alertId: String) {
        viewModelScope.launch { runCatching { favoriteRepository.markAlertRead(alertId) } }
    }
}
