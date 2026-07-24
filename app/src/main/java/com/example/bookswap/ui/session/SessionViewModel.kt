package com.example.bookswap.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.model.User
import com.example.bookswap.data.repository.AuthRepository
import com.example.bookswap.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Top-level app state that decides which flow the user sees. */
sealed interface SessionState {
    /** Still resolving the persisted session. */
    data object Loading : SessionState

    /** No signed-in user — show the auth flow. */
    data object SignedOut : SessionState

    /** Signed in with email/password but the address is not yet verified. */
    data class NeedsEmailVerification(val email: String) : SessionState

    /** Verified, but the required profile fields are not all filled in yet. */
    data class NeedsProfile(val uid: String) : SessionState

    /** Fully onboarded — show the main app. */
    data class Ready(val user: User) : SessionState
}

/**
 * Derives [SessionState] from Firebase auth state + the user's Firestore profile,
 * and drives navigation gating in [com.example.bookswap.MainActivity].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository(),
) : ViewModel() {

    // Bumping this re-subscribes the auth flow so a just-reloaded user (e.g. after
    // clicking the verification link) is re-evaluated.
    private val refresh = MutableStateFlow(0)

    val state: StateFlow<SessionState> = refresh
        .flatMapLatest { authRepository.authState() }
        .flatMapLatest { firebaseUser ->
            when {
                firebaseUser == null -> flowOf(SessionState.SignedOut)
                !firebaseUser.isEmailVerified ->
                    flowOf(SessionState.NeedsEmailVerification(firebaseUser.email.orEmpty()))
                else -> userRepository.observeUser(firebaseUser.uid).map { user ->
                    if (user?.isComplete == true) {
                        SessionState.Ready(user)
                    } else {
                        SessionState.NeedsProfile(firebaseUser.uid)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    /** Reloads the Firebase user and re-evaluates state (used after email verification). */
    fun reloadSession() {
        viewModelScope.launch {
            authRepository.reloadUser()
            refresh.value += 1
        }
    }

    fun signOut() = authRepository.signOut()
}
