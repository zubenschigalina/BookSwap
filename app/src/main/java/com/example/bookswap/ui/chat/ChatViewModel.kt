package com.example.bookswap.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.model.Message
import com.example.bookswap.data.model.Proposal
import com.example.bookswap.data.model.ProposalStatus
import com.example.bookswap.data.repository.AuthRepository
import com.example.bookswap.data.repository.MeetingRepository
import com.example.bookswap.data.repository.MessageRepository
import com.example.bookswap.data.repository.ProposalRepository
import com.example.bookswap.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val currentUserId: String = "",
    val proposal: Proposal? = null,
    /** The agreed meeting place, offered as a one-tap location pin. */
    val meetingLocation: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
) {
    /**
     * Epic 9: "Unlocked ONLY after an exchange proposal is accepted." The composer is
     * hidden unless this holds; `firestore.rules` enforces the same thing server-side.
     */
    val isUnlocked: Boolean
        get() = proposal?.status == ProposalStatus.ACCEPTED.name
}

/**
 * Exchange chat thread (Epic 9). Text, image attachments and location pins, gated on the
 * parent proposal being ACCEPTED.
 *
 * The proposal is observed rather than read once, so if the exchange is cancelled while
 * the thread is open the composer locks itself.
 */
class ChatViewModel(
    private val messageRepository: MessageRepository = MessageRepository(),
    private val proposalRepository: ProposalRepository = ProposalRepository(),
    private val meetingRepository: MeetingRepository = MeetingRepository(),
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var proposalId: String = ""
    private var myName: String = ""
    private var loaded = false

    fun load(proposalId: String) {
        if (loaded) return
        loaded = true
        this.proposalId = proposalId
        val uid = authRepository.currentUser?.uid ?: return
        _uiState.update { it.copy(currentUserId = uid) }

        viewModelScope.launch {
            myName = runCatching { userRepository.getUser(uid)?.firstName }.getOrNull().orEmpty()
        }
        viewModelScope.launch {
            proposalRepository.observeProposal(proposalId).collect { proposal ->
                _uiState.update { it.copy(proposal = proposal) }
            }
        }
        viewModelScope.launch {
            meetingRepository.observeForProposal(proposalId).collect { meeting ->
                _uiState.update { it.copy(meetingLocation = meeting?.locationAddress.orEmpty()) }
            }
        }
        viewModelScope.launch {
            messageRepository.observeThread(proposalId).collect { messages ->
                _uiState.update { it.copy(messages = messages, isLoading = false) }
            }
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        send { messageRepository.sendText(proposalId, it, myName, text) }
    }

    fun sendImage(uri: Uri) {
        send { messageRepository.sendImage(proposalId, it, myName, uri) }
    }

    /** Shares the agreed meeting place as a location pin (Epic 9 "Shared Location Pins"). */
    fun sendMeetingLocation() {
        val label = _uiState.value.meetingLocation
        if (label.isBlank()) return
        send { messageRepository.sendLocation(proposalId, it, myName, label) }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun send(block: suspend (senderId: String) -> Unit) {
        val uid = _uiState.value.currentUserId
        if (uid.isBlank() || !_uiState.value.isUnlocked) return
        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { block(uid) }
            _uiState.update {
                it.copy(isSending = false, error = result.exceptionOrNull()?.message)
            }
        }
    }
}
