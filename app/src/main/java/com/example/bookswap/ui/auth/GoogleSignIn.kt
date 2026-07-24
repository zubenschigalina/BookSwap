package com.example.bookswap.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Runs the Credential Manager "Sign in with Google" flow and returns the Google
 * ID token, which [AuthViewModel.signInWithGoogle] exchanges for a Firebase session.
 *
 * @param webClientId the OAuth Web client ID (res/values/secrets.xml → default_web_client_id).
 * @throws androidx.credentials.exceptions.GetCredentialException if the user cancels or it fails.
 */
suspend fun requestGoogleIdToken(context: Context, webClientId: String): String {
    val option = GetSignInWithGoogleOption.Builder(webClientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    val response = CredentialManager.create(context).getCredential(context, request)
    val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
    return credential.idToken
}
