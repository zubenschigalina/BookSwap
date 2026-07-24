package com.example.bookswap.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookswap.data.model.User
import com.example.bookswap.data.repository.AuthRepository
import com.example.bookswap.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * Backs the profile view/edit screens and the city-change flow (Epic 2). Reads the
 * live user document and writes profile edits + city audit records via [UserRepository].
 */
class ProfileViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val uid: String? get() = authRepository.currentUser?.uid

    init {
        val currentUid = uid
        if (currentUid == null) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                userRepository.observeUser(currentUid).collect { user ->
                    _uiState.update { it.copy(user = user, isLoading = false) }
                }
            }
        }
    }

    /**
     * Saves profile fields. If [photoUri] is set, the photo is uploaded first and its
     * URL stored. On the first save the auth email is captured onto the profile.
     *
     * @param onSaved invoked after a successful save (e.g. to navigate back).
     */
    fun saveProfile(
        firstName: String,
        city: String,
        meetingPoint: String,
        phone: String,
        photoUri: Uri?,
        onSaved: () -> Unit,
    ) {
        val firebaseUser = authRepository.currentUser ?: return
        val existing = _uiState.value.user

        // Required-field validation (PRD Epic 2, Scenario 1). Photo required unless
        // one already exists on the profile.
        val hasPhoto = photoUri != null || existing?.profilePhotoUrl?.isNotBlank() == true
        if (firstName.isBlank() || city.isBlank() || meetingPoint.isBlank() || !hasPhoto) {
            _uiState.update { it.copy(error = "required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val photoUrl = if (photoUri != null) {
                    userRepository.uploadProfilePhoto(firebaseUser.uid, photoUri)
                } else {
                    existing?.profilePhotoUrl.orEmpty()
                }
                val user = User(
                    id = firebaseUser.uid,
                    email = existing?.email?.ifBlank { firebaseUser.email.orEmpty() }
                        ?: firebaseUser.email.orEmpty(),
                    firstName = firstName.trim(),
                    profilePhotoUrl = photoUrl,
                    city = city.trim(),
                    preferredMeetingPoint = meetingPoint.trim(),
                    phoneNumber = phone.trim(),
                    // Preserve reputation fields owned by later sprints.
                    ratingAvg = existing?.ratingAvg ?: 0.0,
                    exchangeCount = existing?.exchangeCount ?: 0,
                    trustScore = existing?.trustScore ?: 0.0,
                    status = existing?.status ?: "ACTIVE",
                    createdAt = existing?.createdAt,
                )
                userRepository.saveProfile(user)
                _uiState.update { it.copy(isSaving = false) }
                onSaved()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.localizedMessage) }
            }
        }
    }

    /**
     * Changes the city and records the reason in city_change_history (Epic 2, Scenario 2).
     */
    fun changeCity(newCity: String, reason: String, onDone: () -> Unit) {
        val currentUid = uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                userRepository.changeCity(currentUid, newCity.trim(), reason.trim())
                _uiState.update { it.copy(isSaving = false) }
                onDone()
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.localizedMessage) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
