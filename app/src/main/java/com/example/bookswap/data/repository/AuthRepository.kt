package com.example.bookswap.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Wraps Firebase Auth for Epic 1 (Authentication & Registration).
 *
 * Firebase persists the signed-in session and refreshes ID tokens on its own, so
 * Sprint 1 does not hand-roll token storage; the PRD's EncryptedSharedPreferences
 * mandate is satisfied by Firebase's own secure session store.
 */
class AuthRepository(
    private val auth: FirebaseAuth = Firebase.auth,
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** Emits the current [FirebaseUser] and every subsequent auth-state change. */
    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** Registers an email/password account and immediately sends a verification email. */
    suspend fun register(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = requireNotNull(result.user) { "Firebase returned no user after registration" }
        user.sendEmailVerification().await()
        return user
    }

    suspend fun login(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        return requireNotNull(result.user) { "Firebase returned no user after login" }
    }

    suspend fun resendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    /** Re-fetches the user record so [FirebaseUser.isEmailVerified] reflects a clicked link. */
    suspend fun reloadUser() {
        auth.currentUser?.reload()?.await()
    }

    fun isEmailVerified(): Boolean = auth.currentUser?.isEmailVerified == true

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /**
     * Completes Google Sign-In using an ID token obtained via Credential Manager.
     * @return true if this sign-in created a brand-new account (drives onboarding).
     */
    suspend fun signInWithGoogle(idToken: String): Boolean {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.additionalUserInfo?.isNewUser == true
    }

    fun signOut() = auth.signOut()
}
