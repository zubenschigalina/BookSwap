package com.example.bookswap.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    /** One-shot info message, e.g. "Password reset email sent." */
    val message: String? = null,
)

/**
 * Handles the email/password + Google auth actions for Epic 1. Navigation between
 * flows is driven by [com.example.bookswap.ui.session.SessionViewModel]; this
 * ViewModel only performs actions and surfaces loading/error state.
 */
class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun register(email: String, password: String) = launchAuth {
        authRepository.register(email, password)
    }

    fun login(email: String, password: String) = launchAuth {
        authRepository.login(email, password)
    }

    fun signInWithGoogle(idToken: String) = launchAuth {
        authRepository.signInWithGoogle(idToken)
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.update { it.copy(error = "Enter your email first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                authRepository.sendPasswordReset(email)
                _uiState.update {
                    it.copy(isLoading = false, message = "Password reset email sent to $email.")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            try {
                authRepository.resendVerificationEmail()
                _uiState.update { it.copy(message = "Verification email re-sent.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.friendlyMessage()) }
            }
        }
    }

    /** Called when a Credential Manager / Google Sign-In error is surfaced from the UI. */
    fun onExternalError(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
    }

    fun setLoading(loading: Boolean) {
        _uiState.update { it.copy(isLoading = loading, error = if (loading) null else it.error) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null, error = null) }

    private fun launchAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, message = null) }
            try {
                block()
                // On success the SessionViewModel picks up the auth-state change and
                // navigates; just clear the spinner.
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.friendlyMessage()) }
            }
        }
    }
}

private fun Exception.friendlyMessage(): String =
    localizedMessage ?: "Something went wrong. Please try again."
