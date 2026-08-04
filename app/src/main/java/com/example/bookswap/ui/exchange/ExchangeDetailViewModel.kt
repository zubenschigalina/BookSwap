package com.example.bookswap.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.model.Meeting
import com.example.bookswap.data.model.MeetingRules
import com.example.bookswap.data.model.Proposal
import com.example.bookswap.data.model.ProposalStatus
import com.example.bookswap.data.repository.AuthRepository
import com.example.bookswap.data.repository.MeetingRepository
import com.example.bookswap.data.repository.ProposalRepository
import com.example.bookswap.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class ExchangeDetailUiState(
    val proposal: Proposal? = null,
    val meeting: Meeting? = null,
    val currentUserId: String = "",
    /** The other party's first name, for headings and chat. */
    val counterpartName: String = "",
    /** Pre-fills the location field from the user's saved preferred meeting point. */
    val defaultMeetingPoint: String = "",
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
) {
    /** Epic 8: only the proposer initiates the first schedule. */
    val isProposer: Boolean
        get() = proposal != null && currentUserId == proposal.proposerId

    val isAccepted: Boolean
        get() = proposal?.status == ProposalStatus.ACCEPTED.name

    /** Epic 9: chat unlocks only once the proposal is accepted. */
    val isChatUnlocked: Boolean
        get() = isAccepted

    val needsMyConfirmation: Boolean
        get() = meeting?.awaitingConfirmationFrom(currentUserId) == true
}

/**
 * One accepted exchange: its meeting (Epic 8), the cancellation rules (Epic 10) and the
 * gate for its chat thread (Epic 9).
 *
 * Both the proposal and the meeting are observed, so a cancellation or confirmation by
 * the other party lands on screen without a manual refresh.
 */
class ExchangeDetailViewModel(
    private val proposalRepository: ProposalRepository = ProposalRepository(),
    private val meetingRepository: MeetingRepository = MeetingRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExchangeDetailUiState())
    val uiState: StateFlow<ExchangeDetailUiState> = _uiState.asStateFlow()

    private var loaded = false

    fun load(proposalId: String) {
        if (loaded) return
        loaded = true
        val uid = authRepository.currentUser?.uid ?: return
        _uiState.update { it.copy(currentUserId = uid) }

        viewModelScope.launch {
            runCatching { userRepository.getUser(uid)?.preferredMeetingPoint.orEmpty() }
                .onSuccess { point -> _uiState.update { it.copy(defaultMeetingPoint = point) } }
        }

        viewModelScope.launch {
            combine(
                proposalRepository.observeProposal(proposalId),
                meetingRepository.observeForProposal(proposalId),
            ) { proposal, meeting -> proposal to meeting }
                .collect { (proposal, meeting) ->
                    _uiState.update {
                        it.copy(proposal = proposal, meeting = meeting, isLoading = false)
                    }
                    resolveCounterpartName(proposal, uid)
                }
        }
    }

    /**
     * The proposal denormalizes only the *proposer's* name, so when the current user IS
     * the proposer the recipient's name still needs a profile read.
     */
    private suspend fun resolveCounterpartName(proposal: Proposal?, uid: String) {
        if (proposal == null || _uiState.value.counterpartName.isNotBlank()) return
        val name = if (uid == proposal.proposerId) {
            runCatching { userRepository.getUser(proposal.recipientId)?.firstName }
                .getOrNull().orEmpty()
        } else {
            proposal.proposerName
        }
        _uiState.update { it.copy(counterpartName = name) }
    }

    /** Epic 8 initiation (proposer only), or a redo while nothing is agreed yet. */
    fun schedule(dateTime: Date, location: String) {
        val proposal = _uiState.value.proposal ?: return
        run(onSuccess = {}) { meetingRepository.schedule(proposal, dateTime, location) }
    }

    /** Epic 8/10 reschedule: offer a change, leaving the agreed schedule active. */
    fun requestChange(dateTime: Date, location: String) {
        val state = _uiState.value
        val meeting = state.meeting ?: return
        run(onSuccess = {}) {
            meetingRepository.requestChange(
                meetingId = meeting.id,
                requestedById = state.currentUserId,
                isProposer = state.isProposer,
                dateTime = dateTime,
                locationAddress = location,
            )
        }
    }

    fun confirm() {
        val state = _uiState.value
        val meeting = state.meeting ?: return
        run(onSuccess = {}) { meetingRepository.confirm(meeting, state.currentUserId) }
    }

    /**
     * Epic 10 cancellation. Whether this is an "emergency" cancel is decided here, at the
     * moment of the tap, from the *active* schedule — not from whatever the UI decided
     * when it drew the dialog.
     */
    fun cancel(reason: String, onCancelled: () -> Unit) {
        val state = _uiState.value
        val meeting = state.meeting ?: return
        val proposal = state.proposal ?: return
        val emergency = MeetingRules.requiresEmergencyReason(meeting.dateTime, Date())

        if (emergency && reason.isBlank()) {
            _uiState.update { it.copy(error = "A reason is required to cancel this late.") }
            return
        }

        run(onSuccess = onCancelled) {
            meetingRepository.cancel(
                meeting = meeting,
                proposal = proposal,
                cancelledById = state.currentUserId,
                reason = reason.trim(),
                isEmergency = emergency,
            )
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /** Shared busy/error wrapper for the write actions above. */
    private fun run(onSuccess: () -> Unit, block: suspend () -> Unit) {
        _uiState.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { block() }
            _uiState.update {
                it.copy(isWorking = false, error = result.exceptionOrNull()?.message)
            }
            if (result.isSuccess) onSuccess()
        }
    }
}
